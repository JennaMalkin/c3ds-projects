/*
 * c3ds-projects - Assorted compatibility fixes & useful tidbits
 * Written starting in 2022 by contributors (see CREDITS.txt)
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide. This software is distributed without any warranty.
 * You should have received a copy of the CC0 Public Domain Dedication along with this software. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package natsue.server.session;

import java.io.IOException;

import natsue.config.Config;
import natsue.data.babel.BabelShortUserData;
import natsue.data.babel.PacketWriter;
import natsue.data.babel.ctos.BaseCTOS;
import natsue.data.babel.ctos.CTOSHandshake;
import natsue.log.ILogProvider;
import natsue.log.ILogSource;
import natsue.server.hubapi.IHubClientAPI;
import natsue.server.hubapi.IHubLoginAPI;
import natsue.server.hubapi.IHubLoginAPI.ILoginReceiver;

/**
 * This session state is to grab the initial handshake packet.
 */
public class LoginSessionState extends BaseSessionState implements ILogSource {
	public final IHubLoginAPI hub;
	public final Config config;

	public LoginSessionState(Config cfg, ISessionClient c, IHubLoginAPI h) {
		super(c);
		hub = h;
		config = cfg;
	}

	@Override
	public ILogProvider getLogParent() {
		return client;
	}

	@Override
	public void handlePacket(BaseCTOS packet) throws IOException {
		if (packet instanceof CTOSHandshake) {
			handleHandshakePacket((CTOSHandshake) packet);
		} else {
			// nope
			client.sendPacket(PacketWriter.writeHandshakeResponse(PacketWriter.HANDSHAKE_RESPONSE_UNKNOWN, 0L, 0L));
			client.setSessionState(null);
		}
	}

	public void handleHandshakePacket(CTOSHandshake handshake) throws IOException {
		// -- attempt normal login --
		IHubLoginAPI.LoginResult res = hub.loginUser(handshake.username, handshake.password, new ILoginReceiver<MainSessionState>() {
			@Override
			public MainSessionState receive(BabelShortUserData userData, IHubClientAPI clientAPI) {
				return new MainSessionState(config, client, clientAPI, userData);
			}
			@Override
			public void confirm(MainSessionState result) {
				client.setSessionState(result);
				try {
					client.sendPacket(PacketWriter.writeHandshakeResponse(PacketWriter.HANDSHAKE_RESPONSE_OK, result.hub.getServerUIN(), result.userData.uin));
				} catch (Exception ex) {
					if (client.logFailedAuth())
						log(ex);
				}
			}
		});
		if (res == IHubLoginAPI.LoginResult.FailedAuth) {
			client.sendPacket(PacketWriter.writeHandshakeResponse(PacketWriter.HANDSHAKE_RESPONSE_INVALID_USER, 0L, 0L));
			client.setSessionState(null);
		} else if (res == IHubLoginAPI.LoginResult.FailedConflict) {
			client.sendPacket(PacketWriter.writeHandshakeResponse(PacketWriter.HANDSHAKE_RESPONSE_ALREADY_LOGGED_IN, 0L, 0L));
			client.setSessionState(null);
		} else if (res != IHubLoginAPI.LoginResult.Success) {
			client.sendPacket(PacketWriter.writeHandshakeResponse(PacketWriter.HANDSHAKE_RESPONSE_UNKNOWN, 0L, 0L));
			client.setSessionState(null);
		}
	}

	@Override
	public void logout() {
		// nothing to do here
	}
}
