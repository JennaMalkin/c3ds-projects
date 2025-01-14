/*
 * c3ds-projects - Assorted compatibility fixes & useful tidbits
 * Written starting in 2022 by contributors (see CREDITS.txt)
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide. This software is distributed without any warranty.
 * You should have received a copy of the CC0 Public Domain Dedication along with this software. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package natsue.server.hubapi;

import java.util.LinkedList;

import natsue.data.babel.pm.PackedMessage;
import natsue.server.cryo.CryoFrontend;
import natsue.server.firewall.IRejector;
import natsue.server.userdata.IHubUserDataCachePrivilegedProxy;
import natsue.server.userdata.INatsueUserData;

/**
 * Represents the server.
 */
public interface IHubPrivilegedAPI extends IHubCommonAPI, IHubUserDataCachePrivilegedProxy, IHubLoginAPI, IRejector {
	/**
	 * Returns all user info that does not belong to system users.
	 */
	LinkedList<INatsueUserData> listAllNonSystemUsersOnlineYesIMeanAllOfThem();

	/**
	 * Adds a client to the system, or returns false if that couldn't happen due to a conflict.
	 * Note that you can't turn back if this returns true, you have to logout again.
	 * The runnable provided here runs at a very specific time such that:
	 * + No functions will quite have been called yet on the client
	 * + The client will definitely be logging in at this point
	 */
	boolean clientLogin(IHubClient client, Runnable confirmOk);

	/**
	 * Route a message that is expected to *eventually* get to the target.
	 * The message is assumed to be authenticated - this is considered to be past the firewall.
	 * If temp is true, the message won't be archived on failure.
	 * If fromRejector is true, then the message won't go through rejection *again*.
	 * causeUIN is used for abuse tracking purposes.
	 */
	void sendMessage(long destinationUIN, PackedMessage message, MsgSendType type, long causeUIN);

	/**
	 * See the other sendMessage definition.
	 * Note that sourceUser is just used as a source for the UIN.
	 * Note also that causeUser is just used as a source for the UIN.
	 */
	default void sendMessage(INatsueUserData destUser, PackedMessage message, MsgSendType type, INatsueUserData causeUser) {
		sendMessage(destUser.getUIN(), message, type, causeUser.getUIN());
	}

	/**
	 * Attempts to forcibly disconnect a user by UIN.
	 * Note that this may not work (system users can shrug it off) but regular users are gone.
	 */
	void forceDisconnectUIN(long uin, boolean sync);

	/**
	 * Properly applies changes to a user's random pool status.
	 */
	void considerRandomStatus(INatsueUserData.LongTerm user);

	/**
	 * Attempst to find anything unusual.
	 */
	String runSystemCheck(boolean detailed);

	/**
	 * Cryo frontend (used by System for cryo-related tasks)
	 */
	CryoFrontend getCryoFE();

	/**
	 * Controls message behaviour.
	 */
	public static enum MsgSendType {
		// Chat/etc.
		Temp(false, MsgSendFailBehaviour.Discard),
		// mail
		Perm(false, MsgSendFailBehaviour.Spool),
		// Norns
		PermReturnIfOffline(false, MsgSendFailBehaviour.Reject),
		// Rejects
		TempReject(true, MsgSendFailBehaviour.Discard),
		PermReject(true, MsgSendFailBehaviour.Spool);

		public final boolean isReject;
		public final MsgSendFailBehaviour failBehaviour;

		MsgSendType(boolean ir, MsgSendFailBehaviour ss) {
			isReject = ir;
			failBehaviour = ss;
		}
	}

	/**
	 * Controls behaviour if we accepted the message but we can't actually send it.
	 */
	public static enum MsgSendFailBehaviour {
		// delete
		Discard(true),
		// spool to disk
		Spool(false),
		// return to sender with shiny note
		Reject(false);
		public final boolean allowMessageLoss;
		MsgSendFailBehaviour(boolean mlm) {
			allowMessageLoss = mlm;
		}
	}
}
