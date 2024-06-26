/*
 * TypeTransformer.java implements the Clite type system that allows
 * implicit type conversion. It transforms the parser generated 
 * abstract syntax tree by adding explicit type conversions as needed.
 * It is defined by the functions T, which use classes in the abstract
 * syntax of Clite and return the transformed syntax tree under that node.
 */

package clite;

import java.util.ArrayList;
import java.util.Collection;

public class TypeTransformer {

	public static Program T(Program p, TypeMap tm) {
		Functions f = p.functions;

		int counter = 0;
		for (Function func : f) {
			// System.out.print("Function " + func.id.toString() + " = \n");
			TypeMap ttMap = new TypeMap();

			for (int i = 0; i < f.size(); i++) {
				ttMap.put(f.get(i).id, f.get(i).t);
			}
			// System.err.print("tm");
			ttMap.putAll(tm);
			// make sure there are locals before putting in map
			if (func.locals != null) {
				if (func.locals.size() > 0) {
					// System.err.print("locals");
					ttMap.putAll(StaticTypeCheck.typing(func.locals));

				}
			}
			// make sure there are parameters before putting in map
			if (func.params != null) {
				if (func.params.size() > 0) {
					ttMap.putAll(StaticTypeCheck.typing(func.params));
				}
			}

			Block newBody = (Block) T(func.body, f, ttMap);
			f.set(counter, (new Function(func.t, func.id, func.params,
					func.locals, newBody)));
			// f.display(counter);
			// System.err.println("Counter: " + counter);
			counter++;
		}

		return new Program(p.globals, f);
	}

	public static Expression T(Expression e, Functions f, TypeMap tm) {
		if (e instanceof Value)
			return e;
		if (e instanceof VariableRef)
			return e;
		if (e instanceof Binary) {
			Binary b = (Binary) e;
			Type typ1 = StaticTypeCheck.typeOf(b.term1, f, tm);
			Type typ2 = StaticTypeCheck.typeOf(b.term2, f, tm);
			Expression t1 = T(b.term1, f, tm);
			Expression t2 = T(b.term2, f, tm);
			// if (typ1 == Type.INT)
			// return new Binary(b.op.intMap(b.op.val), t1,t2);
			// else if (typ1 == Type.FLOAT)
			// return new Binary(b.op.floatMap(b.op.val), t1,t2);
			// else if (typ1 == Type.CHAR)
			// return new Binary(b.op.charMap(b.op.val), t1,t2);
			// else if (typ1 == Type.BOOL)
			// return new Binary(b.op.boolMap(b.op.val), t1,t2);
			if (typ1 == Type.FLOAT || typ2 == Type.FLOAT)
				if (typ1 == Type.INT)
					return new Binary(b.op.floatMap(b.op.val), new Unary(
							new Operator(Operator.I2F), t1), t2);
				else if (typ2 == Type.INT)
					return new Binary(b.op.floatMap(b.op.val), t1, new Unary(
							new Operator(Operator.I2F), t2));
				else
					return new Binary(b.op.floatMap(b.op.val), t1, t2);
			else if (typ1 == Type.INT || typ2 == Type.INT)
				if (typ1 == Type.CHAR)
					return new Binary(b.op.intMap(b.op.val), new Unary(
							new Operator(Operator.C2I), t1), t2);
				else if (typ2 == Type.CHAR)
					return new Binary(b.op.intMap(b.op.val), t1, new Unary(
							new Operator(Operator.C2I), t2));
				else
					return new Binary(b.op.intMap(b.op.val), t1, t2);
			else if (typ1 == Type.CHAR || typ2 == Type.CHAR)
				return new Binary(b.op.charMap(b.op.val), t1, t2);
			else if (typ1 == Type.BOOL || typ2 == Type.BOOL)
				return new Binary(b.op.boolMap(b.op.val), t1, t2);
			throw new IllegalArgumentException("should never reach here");
		}
		if (e instanceof Unary) {
			Unary u = (Unary) e;
			Type typ1 = StaticTypeCheck.typeOf(u.term, f, tm);
			Expression term = T(u.term, f, tm);
			Operator op = u.op;
			if (u.op.equals(Operator.NOT))
				;
			else if (u.op.equals(Operator.NEG)) {
				if (typ1 == Type.INT)
					op = op.intMap(op.val);
				else if (typ1 == Type.FLOAT)
					op = op.floatMap(op.val);
			} else if (u.op.equals(Operator.FLOAT))
				op = op.intMap(op.val);
			else if (u.op.equals(Operator.CHAR))
				op = op.intMap(op.val);
			else if (u.op.equals(Operator.INT)) {
				if (typ1 == Type.FLOAT)
					op = op.floatMap(op.val);
				else if (typ1 == Type.CHAR)
					op = op.charMap(op.val);
			} else {
				throw new IllegalArgumentException("should never reach here");
			}
			return new Unary(op, term);
		}
		if (e instanceof Call) {
			Call c = (Call) e;
			ArrayList<Expression> callArgs = new ArrayList<Expression>();
			// callArgs.addAll(c.arguments);

			// loop through current arguments (c.arguments),
			// arraylist.add(T(put that argument at that position))
			// ->add transformation
			for (int i = 0; i < c.arguments.size(); i++) {
				callArgs.add(T(c.arguments.get(i), f, tm));
			}

			return new Call(c.v, callArgs);
		}
		throw new IllegalArgumentException("should never reach here");
	}

	public static Statement T(Statement s, Functions f, TypeMap tm) {
		if (s instanceof Skip)
			return s;
		if (s instanceof Assignment) {
			Assignment a = (Assignment) s;
			VariableRef target = a.target;
			Expression src = T(a.source, f, tm);

			Type ttype = (Type) tm.get(a.target);
			Type srctype = StaticTypeCheck.typeOf(a.source, f, tm);

			if (ttype == Type.FLOAT) {
				if (srctype == Type.INT) {
					src = new Unary(new Operator(Operator.I2F), src);
					srctype = Type.FLOAT;
				}
			} else if (ttype == Type.INT) {
				if (srctype == Type.CHAR) {
					src = new Unary(new Operator(Operator.C2I), src);
					srctype = Type.INT;
				}
			}
			StaticTypeCheck.check(ttype == srctype, "bug in assignment to "
					+ target);
			return new Assignment(target, src);
		}
		if (s instanceof Conditional) {
			Conditional c = (Conditional) s;
			Expression test = T(c.test, f, tm);
			Statement tbr = T(c.thenbranch, f, tm);
			Statement ebr = T(c.elsebranch, f, tm);
			return new Conditional(test, tbr, ebr);
		}
		if (s instanceof Loop) {
			Loop l = (Loop) s;
			Expression test = T(l.test, f, tm);
			Statement body = T(l.body, f, tm);
			return new Loop(test, body);
		}
		if (s instanceof Block) {
			Block b = (Block) s;
			Block out = new Block();

			for (Statement stmt : b.members)
				out.members.add(T(stmt, f, tm));
			return out;
		}
		if (s instanceof Return) {
			Return r = (Return) s;
			return new Return(r.target, T(r.result, f, tm));
		}
		if (s instanceof Call) {
			Call c = (Call) s;
			ArrayList<Expression> callArgs = new ArrayList<Expression>();
			// callArgs.addAll(c.arguments);

			// loop through current arguments (c.arguments),
			// arraylist.add(T(put that argument at that position))
			// ->add transformation
			for (int i = 0; i < c.arguments.size(); i++) {
				callArgs.add(T(c.arguments.get(i), f, tm));
			}

			return new Call(c.v, callArgs);
		}
		throw new IllegalArgumentException("should never reach here");
	}

} // class TypeTransformer
