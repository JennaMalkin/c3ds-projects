/*
 * c3ds-projects - Assorted compatibility fixes & useful tidbits
 * Written starting in 2022 by contributors (see CREDITS.txt)
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide. This software is distributed without any warranty.
 * You should have received a copy of the CC0 Public Domain Dedication along with this software. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package rals.hcm;

import java.util.Collections;
import java.util.LinkedList;

import rals.code.Macro;
import rals.code.MacroArg;
import rals.code.MacroDefSet;
import rals.code.ScopeContext;
import rals.diag.SrcPos;
import rals.diag.SrcPosFile;
import rals.expr.RALCallable;
import rals.expr.RALConstant;
import rals.expr.RALSlot;
import rals.hcm.HCMStorage.HoverData;
import rals.lex.DefInfo;
import rals.types.AgentInterface;
import rals.types.RALType;

/**
 * Generators of HCM hover data.
 */
public class HCMHoverDataGenerators {
	public static void showSlot(StringBuilder sb, RALSlot slot) {
		sb.append(slot.type);
		sb.append("/");
		sb.append(slot.perms);
	}
	public static void showSlots(StringBuilder sb, RALSlot[] slots) {
		if (slots.length != 1) {
			sb.append("(");
			boolean first = true;
			for (RALSlot rs : slots) {
				if (!first)
					sb.append(", ");
				first = false;
				showSlot(sb, rs);
			}
			sb.append(")");
		} else {
			showSlot(sb, slots[0]);
		}
	}
	public static HCMStorage.HoverData varHoverData(ScopeContext.LVar var) {
		StringBuilder sb = new StringBuilder();
		showSlots(sb, var.content.slots());
		sb.append(" ");
		sb.append(var.name);
		return new HCMStorage.HoverData(sb.toString(), var.definition);
	}
	public static HCMStorage.HoverData constHoverData(String name, RALConstant c, DefInfo di) {
		StringBuilder sb = new StringBuilder();
		showSlots(sb, c.slots());
		sb.append(" ");
		sb.append(name);
		sb.append(" = ");
		sb.append(c.toString());
		return new HCMStorage.HoverData(sb.toString(), di);
	}
	public static HoverData typeHoverData(String k, RALType rt, DefInfo defInfo) {
		return new HCMStorage.HoverData(k + ": " + rt.getFullDescription(), defInfo);
	}
	public static void showMacroArgs(StringBuilder sb, MacroArg[] args) {
		sb.append("(");
		boolean first = true;
		for (MacroArg ma : args) {
			if (!first)
				sb.append(", ");
			first = false;
			sb.append(ma.type);
			if (ma.isInline != null) {
				sb.append("/");
				sb.append(ma.isInline.toString());
			} else {
				sb.append("/R");
			}
			sb.append(" ");
			if (ma.isInline != null) {
				sb.append("@");
				if (ma.isInline == RALSlot.Perm.RW)
					sb.append("@=");
			}
			sb.append(ma.name);
		}
		sb.append(")");
	}
	private static void showCallable(StringBuilder sb, String k, RALCallable rc) {
		if (rc instanceof MacroDefSet) {
			MacroDefSet mds = (MacroDefSet) rc;
			LinkedList<Integer> keys = new LinkedList<>(mds.map.keySet());
			Collections.sort(keys);
			for (Integer ent : keys)
				showCallable(sb, k, mds.map.get(ent));
		} else if (rc instanceof Macro) {
			showSlots(sb, ((Macro) rc).precompiledCode.slots());
			sb.append(" ");
			sb.append(k);
			showMacroArgs(sb, ((Macro) rc).args);
			sb.append("\n");
		} else {
			sb.append(k);
			sb.append(": ");
			sb.append(rc);
			sb.append("\n");
		}
	}
	public static HoverData callableHoverData(String k, RALCallable rc) {
		StringBuilder sb = new StringBuilder();
		showCallable(sb, k, rc);
		return new HCMStorage.HoverData(sb.toString(), rc.getDefInfo());
	}
	public static HoverData fieldHoverData(AgentInterface ai, String me) {
		AgentInterface.OVar ov = ai.fields.get(me);
		return new HCMStorage.HoverData(ai.canonicalType + "." + me + " (OV " + ov.slot + "): " + ov.type, ov.defInfo);
	}
	public static HoverData includeHoverData(SrcPosFile spf) {
		return new HCMStorage.HoverData(spf.docPath.toLSPURI(), new DefInfo.At(new SrcPos(spf, 0, 0, 0), "An included file."));
	}
	public static HoverData msHoverData(AgentInterface ai, String k, AgentInterface.MsgScr me, boolean asScript) {
		return new HCMStorage.HoverData((asScript ? "script" : "message") + " " + ai.canonicalType + (asScript ? ":" : "->") + k + " " + me.value + ";", me.defInfo);
	}
}
