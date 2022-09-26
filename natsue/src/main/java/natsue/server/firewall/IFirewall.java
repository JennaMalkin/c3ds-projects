/*
 * c3ds-projects - Assorted compatibility fixes & useful tidbits
 * Written starting in 2022 by contributors (see CREDITS.txt)
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide. This software is distributed without any warranty.
 * You should have received a copy of the CC0 Public Domain Dedication along with this software. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package natsue.server.firewall;

import natsue.data.babel.pm.PackedMessage;
import natsue.server.hub.IWWRListener;
import natsue.server.userdata.INatsueUserData;

/**
 * Responsible for filtering messages to prevent malicious stuff going through the server.
 */
public interface IFirewall extends IWWRListener {
	/**
	 * Handles a message. NOTE: The message may be modified by this function!
	 */
	public void handleMessage(INatsueUserData sourceUser, long destinationUIN, PackedMessage message);
}
