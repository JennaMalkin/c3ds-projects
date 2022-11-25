/*
 * c3ds-projects - Assorted compatibility fixes & useful tidbits
 * Written starting in 2022 by contributors (see CREDITS.txt)
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide. This software is distributed without any warranty.
 * You should have received a copy of the CC0 Public Domain Dedication along with this software. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package rals.lex;

import java.io.Reader;

import rals.diag.DiagRecorder;
import rals.diag.SrcPos;
import rals.diag.SrcPosFile;
import rals.diag.SrcRange;
import rals.hcm.IHCMRecorder;

/**
 * Big monolith.
 */
public class Lexer {
	private CharHistory charHistory;

	private int tokenHistoryPtr = 4;
	private Token[] tokenHistory = new Token[4];

	private static final String LONERS = ";[]{}(),.";
	private static final String NUM_START = "+-0123456789";
	private static final String NUM_BODY = "0123456789.e";
	private static final String OPERATORS_BREAKING = "<>=?!/*-+:&|^%~";
	private static final String OPERATORS_UNBREAKING = "";
	private static final String OPERATORS = OPERATORS_BREAKING + OPERATORS_UNBREAKING;
	private static final String BREAKERS = "\"\'" + LONERS + OPERATORS_BREAKING;

	private SrcPosFile file;

	/**
	 * Last comment encountered.
	 * Can be reset to null by caller to consume a comment.
	 */
	public String lastComment = null;

	public int levelOfStringEmbedding = 0;
	public int levelOfStringEmbeddingEscape = 0;

	public final DiagRecorder diags;
	public final IHCMRecorder hcm;

	public Lexer(SrcPosFile fn, Reader inp, DiagRecorder d, IHCMRecorder h) {
		charHistory = new CharHistory(inp, 3);
		file = fn;
		diags = d;
		hcm = h;
	}

	public SrcPos genLN() {
		return charHistory.genLN(file);
	}

	public SrcPos endOfLastToken() {
		back();
		return requireNext().extent.end;
	}

	public SrcRange fromThisTokenToLast(Token tkn) {
		return new SrcRange(tkn.extent.start, endOfLastToken());
	}

	private SrcRange completeExtent(SrcPos sp) {
		return new SrcRange(sp, charHistory.genLN(file));
	}

	/**
	 * This now returns a char rather than a byte.
	 * Since the conversion was done by cast anyway, this isn't a problem...
	 */
	private int getNextByte() {
		return charHistory.getNextChar();
	}

	private void backByte() {
		charHistory.backChar();
	}

	private boolean consumeWS() {
		boolean didAnything = false;
		while (true) {
			int b = getNextByte();
			if (b == -1)
				break;
			if (b == '/') {
				// This *could* be a line comment, or it *could* just be something else.
				// Let's find out the difference...
				b = getNextByte();
				if (b == '*') {
					// This is only used for diagnostics, so it doesn't need to be character-precise.
					SrcPos at = genLN();
					StringBuilder sb = new StringBuilder();
					// Block comment.
					while (true) {
						b = getNextByte();
						if (b == -1) {
							diags.error(at, "Unterminated block comment");
							break;
						}
						if (b == '*') {
							b = getNextByte();
							if (b == '/') {
								// End of block comment!
								break;
							} else {
								// just in case it was -1, let next loop grab it
								backByte();
							}
						}
						// not terminating the comment, so
						if ((b != 13))
							sb.append((char) b);
					}
					lastComment = sb.toString();
					didAnything = true;
					continue;
				} else if (b == '/') {
					// Line comment.
					StringBuilder sb = new StringBuilder();
					b = getNextByte();
					while ((b != -1) && (b != 10)) {
						if (b != 13)
							sb.append((char) b);
						b = getNextByte();
					}
					lastComment = sb.toString();
					// ate the newline, that's alright
					didAnything = true;
					continue;
				} else {
					// Whoopsie!
					backByte();
					backByte();
					break;
				}
			}
			if (b > 32) {
				backByte();
				break;
			}
			didAnything = true;
		}
		return didAnything;
	}

	public Token next() {
		if (tokenHistoryPtr < tokenHistory.length) {
			Token res = tokenHistory[tokenHistoryPtr++];
			if (res != null)
				hcm.parserRequestedToken(res, true);
			return res;
		}
		Token tkn = nextInner();
		if (tkn != null) {
			hcm.readToken(tkn);
			hcm.parserRequestedToken(tkn, true);
		}
		for (int i = 0; i < tokenHistory.length - 1; i++)
			tokenHistory[i] = tokenHistory[i + 1];
		tokenHistory[tokenHistory.length - 1] = tkn;
		return tkn;
	}

	public void back() {
		tokenHistoryPtr--;
		// Keep HCM in sync by updating last requested token to the one before the one we're going to return on next().
		// Completion intents that target the token we'll return on next() therefore will anchor on the token before it.
		// (This is expected and is how completion intents work.)
		hcm.parserRequestedToken(tokenHistory[tokenHistoryPtr - 1], false);
	}

	private Token nextInner() {
		consumeWS();
		// Get this after consuming whitespace but before grabbing what will be the start character of the token.
		SrcPos startOfToken = genLN();
		int c = getNextByte();
		if (c == -1)
			return null;
		if (c == '\"') {
			// Regular ol' string
			return finishReadingString(startOfToken, c, false, false);
		} else if (c == '\'') {
			// String w/ string embedding capabilities
			return finishReadingString(startOfToken, c, false, true);
		} else if ((c == '}') && (levelOfStringEmbedding > 0) && (levelOfStringEmbeddingEscape == 0)) {
			// Leaving string embedding argument and entering the string part again
			levelOfStringEmbedding--;
			return finishReadingString(startOfToken, '\'', true, true);
		}
		// This has to punch a gap in the nice little else-if chain.
		// Why? Because it needs to fallthrough if stuff goes wrong...
		if (NUM_START.indexOf(c) != -1) {
			boolean confirmed = false;
			StringBuilder sb = new StringBuilder();
			sb.append((char) c);
			if (NUM_BODY.indexOf(c) == -1) {
				// this isn't a NUM_BODY so check next char
				int c2 = getNextByte();
				sb.append((char) c2);
				if ((c2 == -1) || (NUM_BODY.indexOf(c2) == -1)) {
					// nope
					// Note that because we fallthrough to other token types, 'c' is still in play
					// Hence only go back one byte
					backByte();
				} else {
					confirmed = true;
				}
			} else {
				confirmed = true;
			}
			if (confirmed) {
				// alright, we have a number
				while (true) {
					c = getNextByte();
					if ((c == -1) || (NUM_BODY.indexOf(c) == -1)) {
						backByte();
						break;
					}
					sb.append((char) c);
				}
				String str = sb.toString();
				try {
					return new Token.Int(completeExtent(startOfToken), Integer.parseInt(str));
				} catch (Exception ex) {
					// nope
				}
				try {
					return new Token.Flo(completeExtent(startOfToken), Float.parseFloat(str));
				} catch (Exception ex) {
					// nope
				}
				SrcPos sp = genLN();
				diags.error(sp, "number-like not number");
				return new Token.ID(new SrcRange(startOfToken, sp), str);
			}
		}
		if (LONERS.indexOf(c) != -1) {
			if (levelOfStringEmbedding > 0) {
				if (c == '{')
					levelOfStringEmbeddingEscape++;
				if (c == '}')
					if (levelOfStringEmbeddingEscape > 0)
						levelOfStringEmbeddingEscape--;
			}
			return new Token.Kw(completeExtent(startOfToken), Character.toString((char) c));
		} else if (OPERATORS.indexOf(c) != -1) {
			StringBuilder sb = new StringBuilder();
			sb.append((char) c);
			while (true) {
				c = getNextByte();
				if ((c == -1) || (OPERATORS.indexOf(c) == -1)) {
					backByte();
					break;
				}
				sb.append((char) c);
			}
			String str = sb.toString();
			return new Token.Kw(completeExtent(startOfToken), str);
		} else {
			StringBuilder sb = new StringBuilder();
			sb.append((char) c);
			while (true) {
				c = getNextByte();
				if ((c <= 32) || (BREAKERS.indexOf(c) != -1)) {
					backByte();
					break;
				}
				sb.append((char) c);
			}
			String str = sb.toString();
			if (Token.keywords.contains(str)) {
				return new Token.Kw(completeExtent(startOfToken), str);
			} else {
				return new Token.ID(completeExtent(startOfToken), str);
			}
		}
	}
	private Token finishReadingString(SrcPos startOfToken, int c, boolean startIsClusterEnd, boolean isEmbedding) {
		StringBuilder sb = new StringBuilder();
		boolean escaping = false;
		boolean endIsClusterStart = false;
		while (true) {
			int c2 = getNextByte();
			if (c2 == -1) {
				diags.error(startOfToken, "Unterminated string");
				break;
			}
			if (escaping) {
				if (c2 == 'r')
					c2 = '\r';
				if (c2 == 't')
					c2 = '\t';
				if (c2 == 'n')
					c2 = '\n';
				if (c2 == '0')
					c2 = 0;
				sb.append((char) c2);
				escaping = false;
			} else {
				if (c2 == c) {
					break;
				} else if (isEmbedding && (c2 == '{')) {
					levelOfStringEmbedding++;
					endIsClusterStart = true;
					break;
				} else if (c2 == '\\') {
					escaping = true;
				} else {
					sb.append((char) c2);
				}
			}
		}
		SrcRange sp = completeExtent(startOfToken);
		if (isEmbedding) {
			return new Token.StrEmb(sp, sb.toString(), startIsClusterEnd, endIsClusterStart);
		} else {
			return new Token.Str(sp, sb.toString());
		}
	}

	public Token requireNext() {
		Token tkn = next();
		if (tkn == null)
			throw new RuntimeException("Expected token, got EOF at " + genLN());
		return tkn;
	}

	public Token requireNextKw(String kw) {
		Token tkn = requireNext();
		if (tkn.isKeyword(kw))
			return tkn;
		throw new RuntimeException("Expected " + kw + ", got " + tkn);
	}

	public boolean optNextKw(String string) {
		Token tkn = next();
		if (tkn == null)
			return false;
		if (tkn.isKeyword(string))
			return true;
		back();
		return false;
	}

	public String requireNextID() {
		return requireNextIDTkn().text;
	}

	public Token.ID requireNextIDTkn() {
		Token tkn = requireNext();
		if (tkn instanceof Token.ID)
			return (Token.ID) tkn;
		throw new RuntimeException("Expected ID, got " + tkn);
	}

	public int requireNextInteger() {
		Token tkn = requireNext();
		if (tkn instanceof Token.Int)
			return ((Token.Int) tkn).value;
		throw new RuntimeException("Expected integer, got " + tkn);
	}
}
