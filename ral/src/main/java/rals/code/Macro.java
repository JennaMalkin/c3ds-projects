/*
 * c3ds-projects - Assorted compatibility fixes & useful tidbits
 * Written starting in 2022 by contributors (see CREDITS.txt)
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide. This software is distributed without any warranty.
 * You should have received a copy of the CC0 Public Domain Dedication along with this software. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package rals.code;

import rals.diag.DiagRecorder;
import rals.expr.*;
import rals.types.*;

/**
 * Macros are used to replicate functions because CAOS doesn't have a proper version of those.
 */
public class Macro implements RALCallable {
	public final String name;
	public final MacroArg[] args;
	public final RALExprUR code;

	public Macro(String n, MacroArg[] a, RALExprUR c) {
		name = n;
		args = a;
		code = c;
	}

	@Override
	public void precompile(TypeSystem ts, ScriptsUR source, DiagRecorder diags, Scripts target) {
	}

	@Override
	public RALExprSlice instance(final RALExprSlice a, ScopeContext sc) {
		if (a.length != args.length)
			throw new RuntimeException("Macro " + name + " called with " + a.length + " args, not " + args.length);
		// Our vars are going to be used outside of this context, so we attach vars and such to the parent.
		
		ScopeContext macroContext = new ScopeContext(sc);

		boolean[] inline = new boolean[args.length];
		String[] names = new String[args.length];
		for (int i = 0; i < args.length; i++) {
			inline[i] = args[i].isInline;
			names[i] = args[i].name;
		}

		VarCacher vc = new VarCacher(a, inline, names);
		for (int i = 0; i < args.length; i++)
			macroContext.scopedVariables.put(names[i], vc.finishedOutput.slice(i, 1));

		final RALExprSlice innards = code.resolve(macroContext);

		// If there are no copies, then the wrapping does nothing
		if (vc.copies.length == 0)
			return innards;

		return new Resolved(vc, innards, name);
	}

	public static final class Resolved extends RALExprSlice {
		private final String macroName;
		private final VarCacher vc;
		private final RALExprSlice innards;

		public Resolved(VarCacher vc, RALExprSlice innards, String mn) {
			super(innards.length);
			this.vc = vc;
			this.innards = innards;
			macroName = mn;
		}

		@Override
		protected RALExprSlice sliceInner(int tB, int tL) {
			return new Resolved(vc, innards.slice(tB, tL), macroName);
		}

		@Override
		protected RALExprSlice tryConcatWithInner(RALExprSlice b) {
			if (b instanceof Resolved) {
				if (((Resolved) b).vc == vc) {
					// this is the same instance, so share!
					return new Resolved(vc, RALExprSlice.concat(innards, ((Resolved) b).innards), macroName);
				}
			}
			return super.tryConcatWithInner(b);
		}

		@Override
		public void writeCompileInner(int index, String input, RALType inputExactType, CompileContext context) {
			vc.writeCacheCode(context);
			innards.writeCompile(index, input, inputExactType, context);
		}

		@Override
		protected RALType writeTypeInner(int index) {
			return innards.writeType(index);
		}

		@Override
		public void readCompileInner(RALExprSlice out, CompileContext context) {
			vc.writeCacheCode(context);
			innards.readCompile(out, context);
		}

		@Override
		protected RALType readTypeInner(int index) {
			return innards.readType(index);
		}

		@Override
		public String toString() {
			return "macro arg copier of " + macroName;
		}
	}
}
