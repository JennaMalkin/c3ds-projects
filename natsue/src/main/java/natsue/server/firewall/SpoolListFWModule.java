/*
 * c3ds-projects - Assorted compatibility fixes & useful tidbits
 * Written starting in 2022 by contributors (see CREDITS.txt)
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide. This software is distributed without any warranty.
 * You should have received a copy of the CC0 Public Domain Dedication along with this software. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package natsue.server.firewall;

import natsue.data.babel.pm.PackedMessage;
import natsue.data.babel.pm.PackedMessagePRAY;
import natsue.data.pray.PRAYBlock;
import natsue.server.hubapi.IHubPrivilegedAPI;
import natsue.server.hubapi.IHubPrivilegedAPI.MsgSendType;
import natsue.server.userdata.INatsueUserData;

/**
 * This firewall module ensures that PRAY files with MESG and warp blocks are spooled if necessary.
 */
public class SpoolListFWModule implements IFWModule {
	public final IHubPrivilegedAPI hub;

	public SpoolListFWModule(IHubPrivilegedAPI h) {
		hub = h;
	}

	@Override
	public void wwrNotify(boolean online, INatsueUserData userData) {
	}

	@Override
	public boolean handleMessage(INatsueUserData sourceUser, INatsueUserData destUser, PackedMessage message) {
		if (message instanceof PackedMessagePRAY) {
			PackedMessagePRAY pray = (PackedMessagePRAY) message;
			for (PRAYBlock block : pray.messageBlocks) {
				String type = block.getType();
				if (type.equals("MESG")) {
					// message centre
					hub.sendMessage(destUser, message, MsgSendType.Perm, sourceUser);
					return true;
				} else if (type.equals("warp")) {
					// warped creature
					hub.sendMessage(destUser, message, MsgSendType.PermReturnIfOffline, sourceUser);
					return true;
				}
			}
		}
		return false;
	}
}
