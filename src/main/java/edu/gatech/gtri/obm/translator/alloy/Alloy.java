package edu.gatech.gtri.obm.translator.alloy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import edu.mit.csail.sdg.alloy4.A4Reporter;
import edu.mit.csail.sdg.alloy4.Pos;
import edu.mit.csail.sdg.ast.Command;
import edu.mit.csail.sdg.ast.CommandScope;
import edu.mit.csail.sdg.ast.Decl;
import edu.mit.csail.sdg.ast.Expr;
import edu.mit.csail.sdg.ast.ExprConstant;
import edu.mit.csail.sdg.ast.ExprHasName;
import edu.mit.csail.sdg.ast.ExprList;
import edu.mit.csail.sdg.ast.ExprVar;
import edu.mit.csail.sdg.ast.Func;
import edu.mit.csail.sdg.ast.Module;
import edu.mit.csail.sdg.ast.Sig;
import edu.mit.csail.sdg.ast.Sig.Field;
import edu.mit.csail.sdg.ast.Sig.PrimSig;
import edu.mit.csail.sdg.parser.CompUtil;

public class Alloy {

  protected static final String templateString =
      "open Transfer[Occurrence] as o \n" + "abstract sig Occurrence{}";
  protected static PrimSig occSig;
  protected static Module templateModule;
  public static Module transferModule;

  protected static Set<Sig> ignoredSigs;
  protected static Set<Expr> ignoredExprs;
  protected static Set<Func> ignoredFuncs;

  protected static Func happensBefore;
  protected static Func bijectionFiltered;
  protected static Func funcFiltered;
  protected static Func inverseFunctionFiltered;

  protected Expr overallFact;
  protected List<Sig> allSigs;
  protected Expr _nameExpr;


  public Alloy() {
    A4Reporter rep = new A4Reporter();
    templateModule = CompUtil.parseEverything_fromString(rep, templateString);

    // can not define directory because Module.getAllReachableUserDefinedSigs returns ConstList<Sig>
    // and does not allow to add to the list
    allSigs = new ArrayList<Sig>();
    ignoredSigs = new HashSet<>();
    for (Sig sig : templateModule.getAllReachableUserDefinedSigs()) {
      allSigs.add(sig);
      ignoredSigs.add(sig);
    }
    // add all facts from the occurrence module.
    overallFact = templateModule.getAllReachableFacts();
    ignoredExprs = new HashSet<>();

    ExprList exprList = (ExprList) overallFact;
    for (Expr expr : exprList.args) {
      ignoredExprs.add(expr);
    }


    // abstract
    occSig = (PrimSig) Helper.getReachableSig(templateModule, "this/Occurrence");
    transferModule = Helper.getAllReachableModuleByName(templateModule, "TransferModule");
    ignoredFuncs = new HashSet<>();
    for (Module module : transferModule.getAllReachableModules()) {
      for (Func func : module.getAllFunc()) {
        ignoredFuncs.add(func);
      }
    }

    happensBefore = Helper.getFunction(transferModule, "o/happensBefore");
    bijectionFiltered = Helper.getFunction(transferModule, "o/bijectionFiltered");
    funcFiltered = Helper.getFunction(transferModule, "o/functionFiltered");
    inverseFunctionFiltered = Helper.getFunction(transferModule, "o/inverseFunctionFiltered");


    // constraints
    Func nonZeroDurationOnlyFunction = Helper.getFunction(transferModule, "o/nonZeroDurationOnly");
    Expr nonZeroDurationOnlyFunctionExpression = nonZeroDurationOnlyFunction.call();

    Sig transfer = Helper.getReachableSig(transferModule, "o/Transfer");
    Expr suppressTransfersExpessionBody = transfer.no();
    Func suppressTransfersFunction =
        new Func(null, "suppressTransfers", null, null, suppressTransfersExpessionBody);
    Expr suppressTransfersExpression = suppressTransfersFunction.call();

    Func inputs = Helper.getFunction(transferModule, "o/inputs");
    Func outputs = Helper.getFunction(transferModule, "o/outputs");
    Expr suppressIOExpressionBody = inputs.call().no().and(outputs.call().no());
    Func suppressIOFunction = new Func(null, "suppressIO", null, null, suppressIOExpressionBody);
    Expr suppressIOExpression = suppressIOFunction.call();


    _nameExpr = nonZeroDurationOnlyFunctionExpression.and(suppressTransfersExpression)
        .and(suppressIOExpression);
  }


  public Module getTransferModule() {
    return Alloy.transferModule;
  }

  public Module getTemplateModule() {
    return Alloy.templateModule;
  }


  public PrimSig getOccSig() {
    return Alloy.occSig;
  }

  public Expr getOverAllFact() {
    return this.overallFact;
  }

  public List<Sig> getAllSigs() {
    return this.allSigs;
  }

  protected Sig createSigAndAddToAllSigs(String label, PrimSig parent) {
    // Sig s = new PrimSig("this/" + label, parent);
    Sig s = new PrimSig(label, parent);
    allSigs.add(s);
    return s;
  }

  public Sig createSigAsChildOfOccSigAndAddToAllSigs(String label) {
    return createSigAndAddToAllSigs(label, Alloy.occSig);
  }

  public void addToOverallFact(Expr expr) {
    overallFact = expr.and(overallFact);
  }


  public Set<Sig> getIgnoredSigs() {
    return ignoredSigs;
  }


  public Set<Expr> getIgnoredExprs() {
    return ignoredExprs;
  }


  public Set<Func> getIgnoredFuncs() {
    return ignoredFuncs;
  }

  public void createInverseFunctionFilteredHappensBeforeAndAddToOverallFact(Sig ownerSig, Expr from,
      Expr to) {
    ExprVar s = ExprVar.make(null, "s", ownerSig.type());
    Expr inverseFunctionFilteredExpr =
        inverseFunctionFiltered.call(happensBefore.call(), s.join(from), s.join(to));

    List<ExprHasName> names = new ArrayList<>(List.of(s));
    Decl decl = new Decl(null, null, null, names, ownerSig.oneOf());
    this.addToOverallFact(inverseFunctionFilteredExpr.forAll(decl));
  }

  public void createFunctionFilteredHappensBeforeAndAddToOverallFact(Sig ownerSig, Expr from,
      Expr to) {
    ExprVar s = ExprVar.make(null, "s", ownerSig.type());
    Expr funcFilteredExpr = funcFiltered.call(happensBefore.call(), s.join(from), s.join(to));

    List<ExprHasName> names = new ArrayList<>(List.of(s));
    Decl decl = new Decl(null, null, null, names, ownerSig.oneOf());
    this.addToOverallFact(funcFilteredExpr.forAll(decl));
  }

  public void createBijectionFilteredHappensBeforeAndAddToOverallFact(Sig ownerSig, Expr from,
      Expr to) {
    ExprVar s = ExprVar.make(null, "s", ownerSig.type());
    Expr bijectionFilteredExpr =
        bijectionFiltered.call(happensBefore.call(), s.join(from), s.join(to));

    List<ExprHasName> names = new ArrayList<>(List.of(s));
    Decl decl = new Decl(null, null, null, names, ownerSig.oneOf());
    this.addToOverallFact(bijectionFilteredExpr.forAll(decl));
  }


  public void addToNameExpr(Expr expr) {
    _nameExpr = _nameExpr.and(expr);
  }

  public void addOneConstraintToField(Sig ownerSig, Sig.Field field) {
    ExprVar s = ExprVar.make(null, "s", ownerSig.type());
    List<ExprHasName> names = new ArrayList<>(List.of(s));
    Decl decl = new Decl(null, null, null, names, ownerSig.oneOf());
    this.addToOverallFact(s.join(ownerSig.domain(field)).cardinality()
        .equal(ExprConstant.makeNUMBER(1)).forAll(decl));
  }

  public void addSteps(Sig ownerSig, Map<String, Field> fieldByName) {

    // steps
    Func osteps = Helper.getFunction(transferModule, "o/steps");
    Expr ostepsExpr1 = osteps.call();
    Expr ostepsExpr2 = osteps.call();

    ExprVar s1 = ExprVar.make(null, "s", ownerSig.type());
    List<ExprHasName> names1 = new ArrayList<>(List.of(s1));
    Decl decl1 = new Decl(null, null, null, names1, ownerSig.oneOf());

    Expr expr1 = createStepExpr(s1, ownerSig, fieldByName);
    addToOverallFact((expr1).in(s1.join(ostepsExpr1)).forAll(decl1));


    ExprVar s2 = ExprVar.make(null, "s", ownerSig.type());
    List<ExprHasName> names2 = new ArrayList<>(List.of(s2));
    Decl decl2 = new Decl(null, null, null, names2, ownerSig.oneOf());

    Expr expr2 = createStepExpr(s2, ownerSig, fieldByName);
    this.addToOverallFact(s2.join(ostepsExpr2).in(expr2).forAll(decl2));


  }

  private Expr createStepExpr(ExprVar s, Sig ownerSig, Map<String, Field> fieldByName) {
    Expr expr = null;
    for (Iterator<String> iter = fieldByName.keySet().iterator(); iter.hasNext();) {
      String fieldName = iter.next();
      Sig.Field field = fieldByName.get(fieldName);
      expr =
          expr == null ? s.join(ownerSig.domain(field)) : expr.plus(s.join(ownerSig.domain(field)));
    }

    return expr;
  }

  public void addConstraint(Sig ownerSig, Map<String, Field> fieldByName,
      Map<String, Sig> fieldTypeByFieldName) {

    // During
    Pos pos = null;
    Expr duringExampleExpressions = null;
    for (Iterator<String> iter = fieldByName.keySet().iterator(); iter.hasNext();) {
      String fieldName = iter.next();
      Sig.Field field = fieldByName.get(fieldName);
      String label = fieldName + "DuringExample"; // p1DuringExample
      List<Decl> decls = new ArrayList<>();
      Expr returnDecl = null;

      Expr body = fieldTypeByFieldName.get(fieldName).in(ownerSig.join(field));
      Func duringExamplePredicate = new Func(pos, label, decls, returnDecl, body);
      Expr duringExampleExpression = duringExamplePredicate.call();
      duringExampleExpressions = duringExampleExpressions == null ? duringExampleExpression
          : duringExampleExpressions.and(duringExampleExpression);

    }

    Func instancesDuringExamplePredicate =
        new Func(null, "instancesDuringExample", new ArrayList<>(), null, duringExampleExpressions);
    Expr instancesDuringExampleExpression = instancesDuringExamplePredicate.call();

    Func onlySimpleSequencePredicate = new Func(null, "only" + ownerSig.label, new ArrayList<>(),
        null, ownerSig.cardinality().equal(ExprConstant.makeNUMBER(1)));

    Expr onlySimpleSequenceExpression = onlySimpleSequencePredicate.call();

    addToNameExpr(instancesDuringExampleExpression);
    addToNameExpr(onlySimpleSequenceExpression);
  }


  public Command createCommand(String label) {
    Pos _pos = null;
    String _label = label;
    boolean _check = false;
    int _overall = 6;
    int _bitwidth = -1;
    int _maxseq = -1;
    int _expects = -1;
    Iterable<CommandScope> _scope = Arrays.asList(new CommandScope[] {});
    Iterable<Sig> _additionalExactSig = Arrays.asList(new Sig[] {});
    Expr _formula = _nameExpr.and(getOverAllFact());
    Command _parent = null;

    // ========== Define command ==========

    Command command = new Command(_pos, _nameExpr, _label, _check, _overall, _bitwidth, _maxseq,
        _expects, _scope, _additionalExactSig, _formula, _parent);
    return command;
  }

}


