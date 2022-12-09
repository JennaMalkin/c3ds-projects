/*
 * c3ds-projects - Assorted compatibility fixes & useful tidbits
 * Written starting in 2022 by contributors (see CREDITS.txt)
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide. This software is distributed without any warranty.
 * You should have received a copy of the CC0 Public Domain Dedication along with this software. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package rals.cctx;

/**
 * Yup, it's the same deal as IEHHandle and friends.
 */
public interface ILabelHandle {
	public static final ILabelHandle BREAK = new ILabelHandle() {
		@Override
		public String toString() {
			return "break";
		}
	};
	public static final ILabelHandle CONTINUE = new ILabelHandle() {
		@Override
		public String toString() {
			return "continue";
		}
	};
}
