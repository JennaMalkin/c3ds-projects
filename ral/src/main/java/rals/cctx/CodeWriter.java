/*
 * c3ds-projects - Assorted compatibility fixes & useful tidbits
 * Written starting in 2022 by contributors (see CREDITS.txt)
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide. This software is distributed without any warranty.
 * You should have received a copy of the CC0 Public Domain Dedication along with this software. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package rals.cctx;

import rals.debug.DebugSite;
import rals.debug.IDebugRecorder;

/**
 * It's that thing that does the stuff!
 */
public class CodeWriter {
	private StringBuilder writer;
	public int indent;
	public String queuedCommentForNextLine = null;
	public DebugSite queuedSiteForNextLine = null;
	public final IDebugRecorder debug;

	public int labelNumber = 0;

	public CodeWriter(StringBuilder outText, IDebugRecorder dbg) {
		writer = outText;
		debug = dbg;
	}

	private void writeIndent() {
		for (int i = 0; i < indent; i++)
			writer.append('\t');
	}

	private void writeNLC() {
		if (queuedCommentForNextLine != null) {
			String qc = queuedCommentForNextLine;
			queuedCommentForNextLine = null;
			writeComment(qc);
		}
		if (queuedSiteForNextLine != null) {
			DebugSite ds = queuedSiteForNextLine;
			queuedSiteForNextLine = null;
			debug.saveSiteAndCreateMarker(this, ds);
		}
	}

	public void writeComment(String comment) {
		writeNLC();
		writeIndent();
		writer.append(" * ");
		for (char c : comment.toCharArray()) {
			writer.append(c);
			if (c == '\n') {
				writeIndent();
				writer.append(" * ");
			}
		}
		writer.append('\n');
	}

	public void writeCode(int pre, String text, int post) {
		indent += pre;
		writeCode(text);
		indent += post;
	}

	public void writeCode(int pre, String text) {
		writeCode(pre, text, 0);
	}

	public void writeCode(String text, int post) {
		writeCode(0, text, post);
	}

	public void writeCode(String text) {
		writeNLC();
		writeIndent();
		for (char c : text.toCharArray()) {
			writer.append(c);
			if (c == '\n')
				writeIndent();
		}
		writer.append("\n");
	}
}
