/*
 * c3ds-projects - Assorted compatibility fixes & useful tidbits
 * Written starting in 2022 by contributors (see CREDITS.txt)
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide. This software is distributed without any warranty.
 * You should have received a copy of the CC0 Public Domain Dedication along with this software. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package natsue.server.session;

import java.io.IOException;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import natsue.data.babel.BabelShortUserData;
import natsue.data.babel.PackedMessage;
import natsue.data.babel.PacketWriter;
import natsue.data.babel.ctos.BaseCTOS;
import natsue.data.babel.ctos.CTOSFetchRandomUser;
import natsue.data.babel.ctos.CTOSGetClientInfo;
import natsue.data.babel.ctos.CTOSGetConnectionDetail;
import natsue.data.babel.ctos.CTOSMessage;
import natsue.data.babel.ctos.CTOSWWRModify;
import natsue.log.ILogProvider;
import natsue.log.ILogSource;
import natsue.server.hubapi.IHubClient;
import natsue.server.hubapi.IHubClientAPI;

/**
 * This session state is used while connected to the hub.
 */
public class MainSessionState extends BaseSessionState implements IHubClient, ILogSource {
	public final BabelShortUserData userData;
	public final IHubClientAPI hub;
	public final PingManager pingManager;

	public MainSessionState(ISessionClient c, IHubClientAPI h, BabelShortUserData uin) {
		super(c);
		pingManager = new PingManager(c);
		userData = uin;
		hub = h;
	}

	@Override
	public ILogProvider getLogParent() {
		return client;
	}

	@Override
	public BabelShortUserData getUserData() {
		return userData;
	}

	@Override
	public boolean isSystem() {
		return false;
	}

	@Override
	public void handlePacket(BaseCTOS packet) throws IOException {
		// check for ping-related stuff
		if (pingManager.handleResponse(packet))
			return;

		if (packet instanceof CTOSGetConnectionDetail) {
			CTOSGetConnectionDetail pkt = (CTOSGetConnectionDetail) packet; 
			// well, are they connected?
			boolean result = hub.isUINOnline(pkt.targetUIN);
			if (result) {
				client.sendPacket(pkt.makeOkResponse());
			} else {
				dummyResponse(packet);
			}
		} else if (packet instanceof CTOSGetClientInfo) {
			CTOSGetClientInfo pkt = (CTOSGetClientInfo) packet;
			BabelShortUserData bsud = hub.getShortUserDataByUIN(pkt.targetUIN);
			client.sendPacket(pkt.makeResponse(bsud != null ? bsud.packed : null));
		} else if (packet instanceof CTOSWWRModify) {
			CTOSWWRModify pkt = (CTOSWWRModify) packet;
			// So to avoid someone flooding the system with a really big WWR, I've decided to simply pretend the WWR exists.
			// And actually tell clients about EVERYBODY.
			// But we should also give proper WWR indications when asked.
			if (pkt.add) {
				BabelShortUserData bsud = hub.getShortUserDataByUIN(pkt.targetUIN);
				if (bsud != null)
					wwrNotify(hub.isUINOnline(pkt.targetUIN), bsud);
			}
		} else if (packet instanceof CTOSFetchRandomUser) {
			CTOSFetchRandomUser pkt = (CTOSFetchRandomUser) packet;
			client.sendPacket(pkt.makeResponse(hub.getRandomOnlineNonSystemUIN()));
		} else if (packet instanceof CTOSMessage) {
			CTOSMessage pkt = (CTOSMessage) packet;
			PackedMessage pm = new PackedMessage(pkt.messageData);
			hub.clientGiveMessage(this, pkt.targetUIN, pm);
		} else {
			dummyResponse(packet);
		}
	}
	public void dummyResponse(BaseCTOS packet) throws IOException {
		byte[] dummy = packet.makeDummy();
		if (dummy != null)
			client.sendPacket(dummy);
	}

	@Override
	public void logout() {
		hub.clientLogout(this);
		pingManager.logout();
	}

	@Override
	public void wwrNotify(boolean online, BabelShortUserData userData) {
		// no actual WWR but do give notifications
		try {
			client.sendPacket(PacketWriter.writeUserLine(online, userData.packed));
		} catch (IOException e) {
			log(e);
		}
	}

	@Override
	public void incomingMessage(PackedMessage message, Runnable reject) {
		// First of all, send the message
		try {
			client.sendPacket(PacketWriter.writeMessage(message.toByteArray()));
		} catch (Exception ex) {
			log(ex);
			if (reject != null)
				reject.run();
			return;
		}
		// Now setup tracking for if that fails
		if (reject == null)
			return;
		final AtomicBoolean hasRejected = new AtomicBoolean();
		byte[] pingPacket = pingManager.addPing((status) -> {
			if (hasRejected.getAndSet(true))
				return;
			if (status == 0)
				reject.run();
		});
		if (pingPacket == null) {
			if (!hasRejected.getAndSet(true))
				reject.run();
			return;
		}
		try {
			client.sendPacket(pingPacket);
		} catch (Exception ex) {
			log(ex);
			if (!hasRejected.getAndSet(true))
				reject.run();
		}
	}
}