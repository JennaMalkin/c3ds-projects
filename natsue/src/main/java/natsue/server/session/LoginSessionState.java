/*
 * c3ds-projects - Assorted compatibility fixes & useful tidbits
 * Written starting in 2022 by contributors (see CREDITS.txt)
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide. This software is distributed without any warranty.
 * You should have received a copy of the CC0 Public Domain Dedication along with this software. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package natsue.server.session;

import java.io.IOException;

import natsue.data.babel.BabelShortUserData;
import natsue.data.babel.PacketWriter;
import natsue.data.babel.ctos.BaseCTOS;
import natsue.data.babel.ctos.CTOSHandshake;
import natsue.log.ILogSource;
import natsue.server.hub.IHub;

/**
 * This session state is to grab the initial handshake packet.
 */
public class LoginSessionState extends BaseSessionState implements ILogSource {
	public final IHub hub;

	public LoginSessionState(ISessionClient c, IHub h) {
		super(c);
		hub = h;
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
		if (handshake.username.equals("coral")) {
			client.sendPacket(PacketWriter.writeHandshakeResponse(Integer.valueOf(handshake.password), 0L, 0L));
			client.setSessionState(null);
			return;
		}
		// -- attempt normal login --
		BabelShortUserData data = hub.usernameAndPasswordToShortUserData(handshake.username, handshake.password);
		if (data == null) {
			if (client.logFailedAuth())
				logTo(client, "Failed authentication for username: " + handshake.username);
			client.sendPacket(PacketWriter.writeHandshakeResponse(PacketWriter.HANDSHAKE_RESPONSE_INVALID_USER, 0L, 0L));
			client.setSessionState(null);
			return;
		}
		// -- login ensured --
		final MainSessionState mainHub = new MainSessionState(client, hub, data);
		if (!hub.clientLogin(mainHub, () -> {
			client.setSessionState(mainHub);
			try {
				client.sendPacket(PacketWriter.writeHandshakeResponse(PacketWriter.HANDSHAKE_RESPONSE_OK, hub.getServerUIN(), data.uin));
			} catch (Exception ex) {
				if (client.logFailedAuth())
					logTo(client, ex);
			}
		})) {
			client.sendPacket(PacketWriter.writeHandshakeResponse(PacketWriter.HANDSHAKE_RESPONSE_ALREADY_LOGGED_IN, 0L, 0L));
			client.setSessionState(null);
		}
	}

	@Override
	public void logout() {
		// nothing to do here
	}
}
