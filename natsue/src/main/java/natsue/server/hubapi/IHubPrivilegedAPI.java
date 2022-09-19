/*
 * c3ds-projects - Assorted compatibility fixes & useful tidbits
 * Written starting in 2022 by contributors (see CREDITS.txt)
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide. This software is distributed without any warranty.
 * You should have received a copy of the CC0 Public Domain Dedication along with this software. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package natsue.server.hubapi;

import natsue.data.babel.BabelShortUserData;
import natsue.data.babel.PackedMessage;

/**
 * Represents the server.
 */
public interface IHubPrivilegedAPI extends IHubCommonAPI {
	/**
	 * Route a message that is expected to *eventually* get to the target.
	 * The message is assumed to be authenticated.
	 * If temp is true, the message won't be archived on failure.
	 */
	void sendMessage(long destinationUIN, PackedMessage message, boolean temp);
}