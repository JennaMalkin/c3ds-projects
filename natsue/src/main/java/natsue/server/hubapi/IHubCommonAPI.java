/*
 * c3ds-projects - Assorted compatibility fixes & useful tidbits
 * Written starting in 2022 by contributors (see CREDITS.txt)
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide. This software is distributed without any warranty.
 * You should have received a copy of the CC0 Public Domain Dedication along with this software. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package natsue.server.hubapi;

import java.io.IOException;

import natsue.data.babel.BabelShortUserData;
import natsue.data.babel.PackedMessage;

/**
 * Represents the server.
 */
public interface IHubCommonAPI {
	/**
	 * Gets a UIN by nickname.
	 * The nickname will be automatically folded.
	 * Can and will return null.
	 */
	BabelShortUserData getShortUserDataByNickname(String name);

	/**
	 * Gets the name of a user by their UIN.
	 * Can and will return null.
	 */
	BabelShortUserData getShortUserDataByUIN(long uin);

	/**
	 * Returns true if the given UIN is online.
	 */
	boolean isUINOnline(long uin);

	/**
	 * Given a user's username and password, provides a BabelShortUserData (successful login), or null.
	 * The username will be automatically folded.
	 */
	BabelShortUserData usernameAndPasswordToShortUserData(String username, String password, boolean allowedToRegister);

	/**
	 * Gets a UIN reserved for this server.
	 */
	long getServerUIN();

	/**
	 * Returns a random online UIN that isn't the system.
	 * Returns 0 if none could be found.
	 */
	long getRandomOnlineNonSystemUIN();

	/**
	 * Adds a client to the system, or returns false if that couldn't happen due to a conflict.
	 * Note that you can't turn back if this returns true, you have to logout again.
	 * The runnable provided here runs at a very specific time such that:
	 * + No functions will quite have been called yet on the client
	 * + The client will definitely be logging in at this point
	 */
	boolean clientLogin(IHubClient cc, Runnable confirmOk);
}