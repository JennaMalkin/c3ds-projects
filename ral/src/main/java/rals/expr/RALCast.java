/*
 * c3ds-projects - Assorted compatibility fixes & useful tidbits
 * Written starting in 2022 by contributors (see CREDITS.txt)
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide. This software is distributed without any warranty.
 * You should have received a copy of the CC0 Public Domain Dedication along with this software. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package rals.expr;

import rals.code.*;
import rals.types.*;

/**
 * Typecast.
 */
public final class RALCast implements RALExprUR {
	public final RALExprUR base;
	public final RALType target;
	public final boolean doImplicitCheck;

	private RALCast(RALExprUR b, RALType t) {
		base = b;
		target = t;
		doImplicitCheck = false;
	}

	private RALCast(RALExprUR b, RALType t, boolean imp) {
		base = b;
		target = t;
		doImplicitCheck = imp;
	}

	@Override
	public RALConstant resolveConst(TypeSystem ts) {
		RALConstant rc = base.resolveConst(ts);
		if (rc instanceof RALConstant.Single) {
			if (doImplicitCheck)
				((RALConstant.Single) rc).type.assertImpCast(target);
			return ((RALConstant.Single) rc).cast(target);
		}
		return null;
	}

	/**
	 * Tries to prevent layers from piling up unnecessarily.
	 */
	public static RALCast of(RALExprUR bx, RALType t) {
		return of(bx, t, false);
	}

	/**
	 * Tries to prevent layers from piling up unnecessarily.
	 */
	public static RALCast of(RALExprUR bx, RALType t, boolean checked) {
		// Skip layers of unchecked casts.
		// Checked ones we keep.
		if (bx instanceof RALCast)
			if (!((RALCast) bx).doImplicitCheck)
				bx = ((RALCast) bx).base;
		return new RALCast(bx, t, checked);
	}

	@Override
	public RALExprSlice resolveInner(ScopeContext context) {
		return Resolved.of(base.resolve(context), target, doImplicitCheck);
	}

	public static class Denull implements RALExprUR {
		public final RALExprUR base;

		public Denull(RALExprUR b) {
			base = b;
		}

		@Override
		public RALExprSlice resolveInner(ScopeContext context) {
			RALExprSlice r = base.resolve(context);
			RALType nn = r.assert1ReadType();
			// System.out.println(nn);
			nn = context.world.types.byNonNullable(nn);
			// System.out.println(nn);
			return Resolved.of(r, nn, false);
		}
	}

	public static class Resolved extends RALExprSlice {
		public final RALExprSlice expr;
		public final RALType sourceType;
		public final RALSlot targetSlot;
		private final boolean doImplicitCheck;
		private Resolved(RALExprSlice e, RALType t, boolean c) {
			super(1);
			expr = e;
			if (e.length != 1)
				throw new RuntimeException("Cannot cast " + e + " which has length of " + e.length + ".");
			RALSlot sourceSlot = e.slot(0);
			sourceType = sourceSlot.type;
			// translate casting to permissions
			RALSlot.Perm adjustedPerm = sourceSlot.perms;
			if (c) {
				if (!t.canImplicitlyCast(sourceType))
					adjustedPerm = adjustedPerm.denyWrite();
				if (!sourceType.canImplicitlyCast(t))
					adjustedPerm = adjustedPerm.denyRead();
			}
			targetSlot = new RALSlot(t, adjustedPerm);
			doImplicitCheck = c;
		}

		/**
		 * Tries to prevent layers from piling up unnecessarily.
		 */
		public static Resolved of(RALExprSlice e, RALType t, boolean doImplicitCheck) {
			if (e instanceof Resolved)
				e = ((Resolved) e).expr;
			return new Resolved(e, t, doImplicitCheck);
		}

		@Override
		public String toString() {
			return "Cast" + (doImplicitCheck ? "Imp" : "") + "[" + expr + "!" + targetSlot + "]";
		}

		@Override
		protected RALSlot slotInner(int index) {
			return targetSlot;
		}

		@Override
		public void readCompileInner(RALExprSlice out, CompileContext context) {
			// just to run the checks, because we have to run them late
			readType(0);
			// Invert ourselves so we apply to the target.
			// This is important because it ensures we overwrite inputExactType for storage.
			expr.readCompile(new Resolved(out, targetSlot.type, doImplicitCheck), context);
		}

		@Override
		public void writeCompileInner(int index, String input, RALType inputExactType, CompileContext context) {
			// trigger checks
			writeType(0);
			// Overwriting inputExactType here is what turns, i.e. null|integer (major type unknown) into integer (Int).
			// This is important for set instruction selection.
			expr.writeCompile(0, input, doImplicitCheck ? inputExactType : targetSlot.type, context);
		}

		@Override
		public String getInlineCAOSInner(int index, boolean write, CompileContextNW context) {
			return expr.getInlineCAOS(index, write, context);
		}

		@Override
		public RALSpecialInline getSpecialInlineInner(int index, CompileContextNW context) {
			return expr.getSpecialInline(index, context);
		}
	}
}
