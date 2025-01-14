/*
 * c3ds-projects - Assorted compatibility fixes & useful tidbits
 * Written starting in 2022 by contributors (see CREDITS.txt)
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide. This software is distributed without any warranty.
 * You should have received a copy of the CC0 Public Domain Dedication along with this software. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package natsue.server;

import java.io.File;
import java.net.ServerSocket;
import java.net.Socket;

import natsue.config.*;
import natsue.log.*;
import natsue.server.cryo.CryoFrontend;
import natsue.server.database.INatsueDatabase;
import natsue.server.database.jdbc.JDBCNatsueDatabase;
import natsue.server.firewall.*;
import natsue.server.http.HTTPHandlerImpl;
import natsue.server.hub.ServerHub;
import natsue.server.packet.*;
import natsue.server.session.LoginSessionState;
import natsue.server.system.SystemUserHubClient;

/**
 * It all starts here.
 */
public class Main {
	public static void main(String[] args) throws Exception {
		if (args.length != 0)
			throw new RuntimeException("Natsue Server expects no parameters.");

		ILogProvider ilp = new StdoutLogProvider();
		ILogSource mySource = ilp.logExtend(Main.class.toString());

		mySource.log("Started logger.");

		Config config = new Config();
		IConfigProvider configProvider = new NCFConfigProvider(new File("ntsuconf.txt"));
		config.visit(configProvider);
		configProvider.configFinished();

		mySource.log("Read configuration.");

		INatsueDatabase actualDB = new JDBCNatsueDatabase(ilp, config.db);

		mySource.log("DB abstraction initialized.");

		QuotaManager qm = new QuotaManager(config.connectionQuotas);

		mySource.log("Quota management initialized.");

		CryoFrontend cryo = new CryoFrontend(config, ilp);

		mySource.log("Cryogenics initialized.");

		final ServerHub serverHub = new ServerHub(config, qm, ilp, actualDB, cryo);
		// determine the firewall
		IFWModule[] firewall = null;
		switch (config.firewallLevel.getValue()) {
		case minimal:
			mySource.log("Firewall level: minimal: MINIMAL, HAZARDOUS TO VANILLA CLIENTS");
			firewall = new IFWModule[] {
				new SpoolListFWModule(serverHub)
			};
			break;
		case vanillaSafe:
			mySource.log("Firewall level: vanillaSafe: Should be safe enough.");
			firewall = new IFWModule[] {
				new PRAYBlockListsFWModule(serverHub, false),
				new CreatureCheckingFWModule(serverHub),
				new ComplexFWModule(serverHub),
				new SpoolListFWModule(serverHub)
			};
			break;
		case full:
		default:
			mySource.log("Firewall level: full: No fun allowed.");
			firewall = new IFWModule[] {
				new PRAYBlockListsFWModule(serverHub, true),
				new CreatureCheckingFWModule(serverHub),
				new ComplexFWModule(serverHub),
				new SpoolListFWModule(serverHub)
			};
			break;
		case rejectAll:
			mySource.log("Firewall level: rejectAll: FOR TESTING ONLY");
			firewall = new IFWModule[] {
				new RejectAllFWModule(serverHub)
			};
			break;
		}
		serverHub.setFirewall(firewall, new Rejector(serverHub, SystemUserHubClient.IDENTITY));
		// login the system user
		serverHub.clientLogin(new SystemUserHubClient(config, ilp, serverHub), () -> {});

		HTTPHandlerImpl hhi = new HTTPHandlerImpl(serverHub, config.httpAPIPublic.getValue(), actualDB);

		mySource.log("ServerHub initialized.");

		int port = config.port.getValue();
		try (ServerSocket sv = new ServerSocket(port)) {
			mySource.log("Bound ServerSocket to port " + port + " - ready to accept connections.");

			while (true) {
				Socket skt = sv.accept();
				if (!qm.socketStart(skt)) {
					try {
						try {
							// Abort the connection as hard as possible.
							// We don't want to make another thread for this.
							skt.setSoLinger(true, 0);
						} catch (Exception ex) {
							// nuh-uh
						}
						skt.close();
					} catch (Exception ex) {
						// bye!
					}
					continue;
				}
				new SocketThread(skt, qm, (st) -> {
					return new LoginSessionState(config, st, serverHub);
				}, hhi, ilp, config).start();
			}
		}
	}
}
