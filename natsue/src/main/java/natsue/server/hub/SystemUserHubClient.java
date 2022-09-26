/*
 * c3ds-projects - Assorted compatibility fixes & useful tidbits
 * Written starting in 2022 by contributors (see CREDITS.txt)
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide. This software is distributed without any warranty.
 * You should have received a copy of the CC0 Public Domain Dedication along with this software. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package natsue.server.hub;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.LinkedList;
import java.util.Random;

import natsue.config.Config;
import natsue.data.babel.BabelShortUserData;
import natsue.data.babel.PacketReader;
import natsue.data.babel.UINUtils;
import natsue.data.babel.WritVal;
import natsue.data.babel.pm.PackedMessage;
import natsue.data.babel.pm.PackedMessagePRAY;
import natsue.data.hli.StandardMessages;
import natsue.data.pray.PRAYBlock;
import natsue.data.pray.PRAYTags;
import natsue.log.ILogProvider;
import natsue.log.ILogSource;
import natsue.names.PWHash;
import natsue.server.database.NatsueDBUserInfo;
import natsue.server.hubapi.IHubClient;
import natsue.server.hubapi.IHubPrivilegedClientAPI;
import natsue.server.hubapi.IHubPrivilegedAPI.MsgSendType;
import natsue.server.userdata.INatsueUserData;

/**
 * This client represents a user called System meant to handle fancy tasks.
 */
public class SystemUserHubClient implements IHubClient, ILogSource {
	public final IHubPrivilegedClientAPI hub;
	private final ILogProvider logParent;
	public static final long UIN = UINUtils.SERVER_UIN;
	public static final INatsueUserData.Root IDENTITY = new INatsueUserData.Fixed(new BabelShortUserData("", "", "!System", UIN), FLAG_RECEIVE_NB_NORNS);
	public final int maxDecompressedPRAYSize;

	private final Random random = new Random();
	private final Object randomLock = new Object();

	private final String COL_NICKNAME = "<tint 120 220 250>";
	private final String COL_CHAT = "<tint 96 160 192>";

	public SystemUserHubClient(Config config, ILogProvider log, IHubPrivilegedClientAPI h) {
		hub = h;
		logParent = log;
		maxDecompressedPRAYSize = config.maxDecompressedPRAYSize.getValue();
	}

	@Override
	public ILogProvider getLogParent() {
		return logParent;
	}

	@Override
	public INatsueUserData.Root getUserData() {
		return IDENTITY;
	}

	@Override
	public boolean isSystem() {
		return true;
	}

	@Override
	public boolean forceDisconnect(boolean sync) {
		// You can't disconnect !System, that'd be absurd
		return false;
	}

	@Override
	public void wwrNotify(boolean online, INatsueUserData theirData) {
		hub.sendMessage(theirData.getUIN(), StandardMessages.addToContactList(theirData.getUIN(), UIN), MsgSendType.Temp);
		/*
		writ = WritVal.encodeWrit("system_message", 2469, "You didn't say the magic word!", null);
		try {
			hub.forceRouteMessage(theirData.uin, new PackedMessage(theirData.uin, PackedMessage.TYPE_WRIT, writ));
		} catch (Exception ex) {
			logTo(hub.log, ex);
		}*/
	}

	@Override
	public void incomingMessage(PackedMessage message, Runnable reject) {
		if (message instanceof PackedMessagePRAY) {
			try {
				LinkedList<PRAYBlock> info = ((PackedMessagePRAY) message).messageBlocks;
				// Detect creatures we're about to lose
				for (PRAYBlock pb : info) {
					if (pb.getType().equals("GLST")) {
						// Trapped creature - RETURN TO SENDER IMMEDIATELY
						hub.rejectMessage(UIN, message, "!System isn't accepting creatures");
						return;
					}
				}
				// No? Ok, is it chat?
				if (info.size() == 1) {
					PRAYBlock chatMaybe = info.getFirst();
					String chatType = chatMaybe.getType();
					if (chatType.equals("REQU")) {
						// Chat request to System?
						PRAYTags pt = new PRAYTags();
						pt.read(chatMaybe.data);
						String str = pt.strMap.get("Request Type");
						if (str.equals("Request")) {
							// Yes - we need to accept.
							PRAYTags res = new PRAYTags();
							String myUINStr = UINUtils.toString(UIN);
							res.strMap.put("Sender UserID", myUINStr);
							res.strMap.put("Date Sent", pt.strMap.get("Date Sent"));
							res.strMap.put("ChatID", pt.strMap.get("ChatID"));
							res.strMap.put("Request Type", "Accept");
							res.strMap.put("Sender Nickname", getNickname());
							sendTagsMessage(message.senderUIN, "REQU", res.toByteArray());
						}
					} else if (chatType.equals("CHAT")) {
						PRAYTags pt = new PRAYTags();
						pt.read(chatMaybe.data);
						String chatID = pt.strMap.get("ChatID");
						String text = pt.strMap.get("Chat Message");
						if ((chatID != null) && (text != null)) {
							// strip initial tint
							if (text.contains(">"))
								text = text.substring(text.indexOf('>') + 1);
							text = text.trim();
							handleChatMessage(message.senderUIN, chatID, text);
						}
					} else if (chatType.equals("MESG")) {
						PRAYTags pt = new PRAYTags();
						pt.read(chatMaybe.data);
						String subject = pt.strMap.get("Subject");
						String msg = pt.strMap.get("Message");
						if ((subject != null) && (msg != null)) {
							if (subject.equalsIgnoreCase("SYSTEM MSG")) {
								if (hub.isUINAdmin(message.senderUIN)) {
									for (INatsueUserData sud : hub.listAllNonSystemUsersOnlineYesIMeanAllOfThem()) {
										hub.sendMessage(sud.getUIN(), StandardMessages.systemMessage(sud.getUIN(), msg), MsgSendType.Temp);
									}
								} else {
									hub.rejectMessage(UIN, message, "Have to be admin");
								}
							}
						}
					}
				}
			} catch (Exception ex) {
				log(ex);
			}
		}
	}

	private INatsueUserData.LongTermPrivileged commandLookupUserLongTerm(String ref) {
		long asUIN = UINUtils.valueOf(ref);
		if (asUIN != -1)
			return hub.openUserDataByUINLT(asUIN);
		return hub.openUserDataByNicknameLT(ref);
	}

	private INatsueUserData commandLookupUser(String ref) {
		long asUIN = UINUtils.valueOf(ref);
		if (asUIN != -1)
			return hub.getUserDataByUIN(asUIN);
		return hub.getUserDataByNickname(ref);
	}

	private void handleChatMessage(long targetUIN, String chatID, String text) {
		StringBuilder response = new StringBuilder();
		if (text.startsWith("whois ")) {
			String user = text.substring(6);
			INatsueUserData userData = commandLookupUser(user);
			if (userData != null) {
				response.append(userData.getNickname());
				response.append(COL_CHAT);
				response.append(" - ");
				if (hub.isUINOnline(userData.getUIN())) {
					response.append("<tint 64 255 64>Online\n");
				} else {
					response.append("<tint 255 64 64>Offline\n");
				}
				response.append(COL_CHAT);
				response.append("UIN: ");
				response.append(userData.getUINString());
				int hid = UINUtils.hid(userData.getUIN()); 
				if (hid == UINUtils.HID_SYSTEM) {
					response.append(" <tint 64 64 255>(SYSTEM)\n");
				} else if (hid == UINUtils.HID_USER) {
					response.append(" <tint 64 255 64>(USER)\n");
				} else {
					response.append(" <tint 255 64 64>(" + hid + ")\n");
				}
				response.append(COL_CHAT);
				response.append("Flags:");
				if (userData.isAdmin())
					response.append(" ADMIN");
				if (userData.isFrozen())
					response.append(" FROZEN");
				if (userData.isReceivingNBNorns())
					response.append(" RECVNB");
				response.append("\n");
			} else {
				appendNoSuchUser(response, user);
			}
		} else if (text.startsWith("contact ")) {
			String user = text.substring(8);
			INatsueUserData userData = commandLookupUser(user);
			if (userData != null) {
				response.append(COL_CHAT);
				response.append("Adding to your contact list...\n");
				hub.sendMessage(targetUIN, StandardMessages.addToContactList(targetUIN, userData.getUIN()), MsgSendType.Temp);
			} else {
				appendNoSuchUser(response, user);
			}
		} else if (text.startsWith("kick ")) {
			if (!hub.isUINAdmin(targetUIN)) {
				response.append(COL_CHAT);
				response.append("You're not allowed to do that!\n");
			} else {
				String user = text.substring(5);
				INatsueUserData userData = commandLookupUser(user);
				if (userData != null) {
					response.append(COL_CHAT);
					response.append("Kicking (if online).\n");
					hub.forceDisconnectUIN(userData.getUIN(), false);
				} else {
					appendNoSuchUser(response, user);
				}
			}
		} else if (text.startsWith("resetpw ")) {
			if (!hub.isUINAdmin(targetUIN)) {
				response.append(COL_CHAT);
				response.append("You're not allowed to do that!\n");
			} else {
				String user = text.substring(8);
				try (INatsueUserData.LongTermPrivileged userData = commandLookupUserLongTerm(user)) {
					if (userData != null) {
						String newPW;
						try {
							newPW = Long.toHexString(SecureRandom.getInstanceStrong().nextLong() | 0x8000000000000000L);
							if (userData.setPassword(newPW)) {
								response.append(COL_CHAT);
								response.append("Reset password to: " + newPW + "\n");
							} else {
								response.append(COL_CHAT);
								response.append("Failed (not a normal user?)\n");
							}
						} catch (NoSuchAlgorithmException e) {
							throw new RuntimeException(e);
						}
					} else {
						appendNoSuchUser(response, user);
					}
				}
			}
		} else if (text.startsWith("setpw ")) {
			String newPW = text.substring(6);
			try (INatsueUserData.LongTermPrivileged ltp = hub.openUserDataByUINLT(targetUIN)) {
				if ((ltp != null) && ltp.setPassword(newPW)) {
					response.append(COL_CHAT);
					response.append("Reset password to: " + newPW + "\n");
				} else {
					response.append(COL_CHAT);
					response.append("Failed (not a normal user?)\n");
				}
			}
		} else if (text.equals("who")) {
			boolean first = true;
			for (INatsueUserData data : hub.listAllNonSystemUsersOnlineYesIMeanAllOfThem()) {
				if (!first) {
					response.append(COL_CHAT);
					response.append(", ");
					response.append(COL_NICKNAME);
				}
				response.append(data.getNickname());
				first = false;
			}
			response.append("\n");
		} else if (text.equals("allownbnorns")) {
			try (INatsueUserData.LongTermPrivileged ltp = hub.openUserDataByUINLT(targetUIN)) {
				if ((ltp != null) && ltp.setFlags(NatsueDBUserInfo.FLAG_RECEIVE_NB_NORNS)) {
					response.append(COL_CHAT);
					response.append("NB norn receipt enabled\n");
				} else {
					response.append(COL_CHAT);
					response.append("Failed.\n");
				}
			}
		} else if (text.equals("denynbnorns")) {
			try (INatsueUserData.LongTermPrivileged ltp = hub.openUserDataByUINLT(targetUIN)) {
				if ((ltp != null) && ltp.unsetFlags(NatsueDBUserInfo.FLAG_RECEIVE_NB_NORNS)) {
					response.append(COL_CHAT);
					response.append("NB norn receipt disabled\n");
				} else {
					response.append(COL_CHAT);
					response.append("Failed.\n");
				}
			}
		} else if (text.equals("kickme")) {
			hub.forceDisconnectUIN(targetUIN, false);
		} else if (text.equals("whoami")) {
			response.append(COL_CHAT);
			response.append("A philosophical question.\n");
			response.append("To me? You are " + UINUtils.toString(targetUIN) + ".\n");
			response.append("Others may say differently.\n");
		} else if (text.equals("ahelp")) {
			response.append(COL_CHAT);
			if (hub.isUINAdmin(targetUIN)) {
				response.append("admin commands:\n");
				response.append("kick Someone\n");
				response.append("resetpw Someone\n");
				response.append("You can send a global system message by mail, subject \"SYSTEM MSG\".\n");
			} else {
				response.append("What are you doing here?\n");
			}
		} else {
			response.append(COL_CHAT);
			response.append("Unknown command. Try:\n");
			response.append("whois !System\n");
			response.append("contact !System\n");
			response.append("who (show who's online)\n");
			response.append("(allow/deny)nbnorns (WARNING: Crashes you if unmodded!)\n");
			response.append("setpw 1234 (sets your password)\n");
			if (hub.isUINAdmin(targetUIN))
				response.append("For admin tasks try: ahelp\n");
		}
		sendChatMessage(targetUIN, chatID, response.toString());
	}

	private void appendNoSuchUser(StringBuilder response, String user) {
		response.append(COL_CHAT);
		response.append("'");
		response.append(COL_NICKNAME);
		response.append(user);
		response.append(COL_CHAT);
		response.append("' doesn't exist. Specify a nickname or UIN.\n");
	}

	private void sendChatMessage(long targetUIN, String chatID, String text) {
		// uuuh
		PRAYTags res = new PRAYTags();
		res.strMap.put("Chat Message Type", "Message");
		res.strMap.put("Sender UserID", UINUtils.toString(UIN));
		res.strMap.put("ChatID", chatID);
		res.strMap.put("Sender Nickname", IDENTITY.getNickname());
		res.strMap.put("Chat Message", text);
		sendTagsMessage(targetUIN, "CHAT", res.toByteArray());
	}

	private void sendTagsMessage(long senderUIN, String type, byte[] res) {
		long randomRes;
		synchronized (randomLock) {
			randomRes = random.nextLong();
		}
		PRAYBlock pb = new PRAYBlock(type, "STM_" + randomRes + "_sysrsp", res);
		hub.sendMessage(senderUIN, new PackedMessagePRAY(UIN, pb), MsgSendType.Temp);
	}
}
