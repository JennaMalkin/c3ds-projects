/*
 * c3ds-projects - Assorted compatibility fixes & useful tidbits
 * Written starting in 2022 by contributors (see CREDITS.txt)
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide. This software is distributed without any warranty.
 * You should have received a copy of the CC0 Public Domain Dedication along with this software. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package natsue.server.system.cmd;

import java.util.List;

import natsue.data.babel.UINUtils;
import natsue.data.hli.ChatColours;
import natsue.log.ILogSource;
import natsue.server.hubapi.IHubPrivilegedClientAPI;
import natsue.server.userdata.INatsueUserData;

/**
 * The base for all System commands.
 */
public abstract class BaseBotCommand {
	public final String name, helpArgs, helpSummary, helpText, helpExample;
	public final Cat category;

	public BaseBotCommand(String n, String ha, String hs, String ht, String he, Cat ao) {
		name = n;
		helpArgs = ha;
		helpSummary = hs;
		helpText = ht;
		helpExample = he;
		category = ao;
	}
	public abstract void run(Context args);

	public static enum Cat {
		Public,
		Secret,
		Admin
	}

	public static class Context {
		/**
		 * Hub/etc.
		 */
		public final IHubPrivilegedClientAPI hub;

		/**
		 * Command sender UIN.
		 */
		public final long senderUIN;

		/**
		 * Response buffer.
		 */
		public final StringBuilder response = new StringBuilder();

		/**
		 * Accumulated text.
		 */
		public final char[] text;

		/**
		 * Current position in the text array.
		 */
		public int index = 0;

		/**
		 * Log source for commands that log stuff.
		 */
		public final ILogSource log;

		/**
		 * Information for the help command.
		 */
		public final List<BaseBotCommand> helpInfo;

		public Context(IHubPrivilegedClientAPI h, long s, String tex, ILogSource lSrc, List<BaseBotCommand> hi) {
			log = lSrc;
			response.append(ChatColours.CHAT);
			hub = h;
			senderUIN = s;
			helpInfo = hi;
			// strip initial tint
			if (tex.contains(">"))
				tex = tex.substring(tex.indexOf('>') + 1);
			tex = tex.trim();
			// and confirm
			text = tex.toCharArray();
		}

		/**
		 * Just eats all whitespace.
		 */
		private void consumeWhitespace() {
			while (index < text.length) {
				if (text[index] <= 32) {
					index++;
				} else {
					break;
				}
			}
		}

		/**
		 * Just eats all text.
		 */
		private void consumeText() {
			while (index < text.length) {
				if (text[index] > 32) {
					index++;
				} else {
					break;
				}
			}
		}

		/**
		 * Returns true if there are remaining arguments.
		 */
		public boolean remaining() {
			consumeWhitespace();
			return index < text.length;
		}

		/**
		 * Gets the next arg, or null if none!
		 */
		public String nextArg() {
			consumeWhitespace();
			if (index == text.length)
				return null;
			int startIndex = index;
			consumeText();
			return new String(text, startIndex, index - startIndex);
		}

		/**
		 * Returns all characters to the end of the line.
		 */
		public String toEnd() {
			consumeWhitespace();
			return new String(text, index, text.length - index);
		}

		public void appendNoSuchUser(String user) {
			response.append(ChatColours.CHAT);
			response.append("'");
			response.append(ChatColours.NICKNAME);
			response.append(user);
			response.append(ChatColours.CHAT);
			response.append("' doesn't exist. Specify a nickname or UIN.\n");
		}

		public INatsueUserData.LongTermPrivileged commandLookupUserLongTerm(String ref) {
			long asUIN = UINUtils.valueOf(ref);
			if (asUIN != -1)
				return hub.openUserDataByUINLT(asUIN);
			return hub.openUserDataByNicknameLT(ref);
		}

		public INatsueUserData commandLookupUser(String ref) {
			long asUIN = UINUtils.valueOf(ref);
			if (asUIN != -1)
				return hub.getUserDataByUIN(asUIN);
			return hub.getUserDataByNickname(ref);
		}
	}
}
