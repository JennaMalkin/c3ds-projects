/*
 * c3ds-projects - Assorted compatibility fixes & useful tidbits
 * Written starting in 2022 by contributors (see CREDITS.txt)
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide. This software is distributed without any warranty.
 * You should have received a copy of the CC0 Public Domain Dedication along with this software. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package rals.parser;

import java.util.LinkedList;

import rals.expr.RALConstant;
import rals.expr.RALExpr;
import rals.expr.RALExprUR;
import rals.lex.Lexer;
import rals.lex.Token;
import rals.stmt.RALAliasStatement;
import rals.stmt.RALAssignStatement;
import rals.stmt.RALBlock;
import rals.stmt.RALInlineStatement;
import rals.stmt.RALLetStatement;
import rals.stmt.RALStatement;
import rals.stmt.RALStatementUR;
import rals.types.RALType;
import rals.types.TypeSystem;

/**
 * Code parser.
 */
public class ParserCode {
	public static RALStatementUR parseStatement(TypeSystem ts, Lexer lx) {
		Token tkn = lx.requireNext();
		if (tkn.isKeyword("{")) {
			RALBlock rb = new RALBlock(tkn.lineNumber, true);
			while (true) {
				tkn = lx.requireNext();
				if (tkn.isKeyword("}"))
					break;
				lx.back();
				rb.content.add(parseStatement(ts, lx));
			}
			return rb;
		} else if (tkn.isKeyword("inline")) {
			LinkedList<Object> obj = new LinkedList<>();
			while (true) {
				Token tkn2 = lx.requireNext();
				if (tkn2.isKeyword(";")) {
					break;
				} else if (tkn2 instanceof Token.Str) {
					obj.add(((Token.Str) tkn2).text);
				} else {
					lx.back();
					obj.add(ParserExpr.parseExpr(ts, lx, true));
				}
			}
			return new RALInlineStatement(tkn.lineNumber, obj.toArray());
		} else if (tkn.isKeyword("let")) {
			LinkedList<String> names = new LinkedList<>();
			LinkedList<RALType> types = new LinkedList<>();
			RALExprUR re = null;
			while (true) {
				RALType rt = ParserType.parseType(ts, lx);
				String n = lx.requireNextID();
				names.add(n);
				types.add(rt);
				Token chk = lx.requireNext();
				if (chk.isKeyword("=")) {
					re = ParserExpr.parseExpr(ts, lx, true);
					lx.requireNextKw(";");
					break;
				} else if (chk.isKeyword(";")) {
					break;
				} else if (!chk.isKeyword(",")) {
					throw new RuntimeException("Expected = or ,");
				}
			}
			return new RALLetStatement(tkn.lineNumber, names.toArray(new String[0]), types.toArray(new RALType[0]), re);
		} else if (tkn.isKeyword("alias")) {
			String id = lx.requireNextID();
			lx.requireNextKw("=");
			RALExprUR res = ParserExpr.parseExpr(ts, lx, true);
			lx.requireNextKw(";");
			return new RALAliasStatement(tkn.lineNumber, id, res);
		} else {
			lx.back();
			RALExprUR target = ParserExpr.parseExpr(ts, lx, false);
			Token sp = lx.requireNext();
			if (sp.isKeyword(";")) {
				return new RALAssignStatement(tkn.lineNumber, null, target);
			} else if (sp.isKeyword("=")) {
				RALExprUR source = ParserExpr.parseExpr(ts, lx, true);
				return new RALAssignStatement(tkn.lineNumber, target.decomposite(), source);
			} else {
				throw new RuntimeException("Saw expression at " + tkn + " but then was wrong about it.");
			}
		}
	}
}