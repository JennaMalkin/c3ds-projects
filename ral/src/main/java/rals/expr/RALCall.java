/*
 * c3ds-projects - Assorted compatibility fixes & useful tidbits
 * Written starting in 2022 by contributors (see CREDITS.txt)
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide. This software is distributed without any warranty.
 * You should have received a copy of the CC0 Public Domain Dedication along with this software. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package rals.expr;

import java.io.StringWriter;

import rals.code.ScopeContext;

/**
 * Calls a macro, or something like that.
 */
public class RALCall implements RALExprUR {
	public final String name;
	public final RALExprUR[] params;

	public RALCall(String n, RALExprUR p) {
		name = n;
		params = p.decomposite();
	}

	@Override
	public RALExpr resolve(ScopeContext context) {
		RALExpr[] paramR = new RALExpr[params.length];
		for (int i = 0; i < paramR.length; i++)
			paramR[i] = params[i].resolve(context);
		RALCallable rc = context.script.module.callable.get(name);
		if (rc == null)
			throw new RuntimeException("No such callable: " + name);
		return rc.instance(paramR, context);
	}
}