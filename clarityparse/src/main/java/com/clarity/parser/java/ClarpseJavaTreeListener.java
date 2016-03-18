package com.clarity.parser.java;

import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.TerminalNode;

import parser.java.JavaBaseListener;
import parser.java.JavaParser;
import parser.java.JavaParser.AnnotationTypeDeclarationContext;

import com.clarity.ClarityUtil;
import com.clarity.parser.AntlrUtil;
import com.clarity.sourcemodel.Component;
import com.clarity.sourcemodel.OOPSourceCodeModel;
import com.clarity.sourcemodel.OOPSourceModelConstants;
import com.clarity.sourcemodel.TypeReference;

/**
 * As the parse tree is developed by Antlr, we add listener methods to capture
 * important information during this process and populate our Source Code Model.
 *
 * @author Muntazir Fadhel
 */
public class ClarityJavaListener extends JavaBaseListener {

    private static Logger log = Logger.getLogger(ClarityJavaListener.class.getName());
    private final Stack<Component> componentStack = new Stack<Component>();
    private final ArrayList<String> currentImports = new ArrayList<String>();
    private String currentPkg;
    private String currFileSourceCode;
    private final OOPSourceCodeModel srcModel;
    private int componentCompletionMultiplier = 1;
    private final Map<String, String> currentImportsMap = new HashMap<String, String>();
    private boolean ignoreTreeWalk = false;
    private static final String JAVA_BLOCK_COMMENT_BEGIN_SYMBOL = "/*";
    private static final String JAVA_BLOCK_COMMENT_END_SYMBOL = "*/";

    /**
     * Constructor.
     *
     * @param srcModel
     *            Source model to populate from the parsing of the given code
     *            base.
     */
    public ClarityJavaListener(final OOPSourceCodeModel srcModel) {
        this.srcModel = srcModel;
    }

    /**
     * Cleanup tasks to do before completing and removing the component from the
     * stack: 1) Set its unique name 2) Update parent components to point
     * towards the current component as their child component.
     */
    private void completeComponent() {
        for (int i = 0; i < componentCompletionMultiplier; i++) {
            if (!componentStack.isEmpty()) {
                final Component completedCmp = componentStack.pop();
                completedCmp.setUniqueName();
                final String completedCmpParentName = ClarityUtil.getParentComponentUniqueName(completedCmp
                        .getUniqueName());
                for (final Component possibleParentCmp : componentStack) {
                    if (possibleParentCmp.getUniqueName().equals(completedCmpParentName)) {
                        possibleParentCmp.insertChildComponent(completedCmp);
                    }
                }
                srcModel.insertComponent(completedCmp);
            }
        }
        componentCompletionMultiplier = 1;
    }

    /**
     * Generates appropriate name for the component. Uses the current stack of
     * parents components as prefixes to the name.
     *
     * @param identifier
     *            short hand name of the component
     * @return full name of the component
     */
    private String generateComponentName(final String identifier) {
        String componentName = "";
        if (!componentStack.isEmpty()) {
            for (final Component cmp : componentStack) {
                componentName = componentName + cmp.getName() + ".";
            }
            componentName = componentName + identifier;
        } else {
            componentName = identifier;
        }
        return componentName;
    }

    /**
     * Creates a new component.
     *
     * @param ctx
     *            Rule context
     * @param componentType
     *            type of component
     * @return the newly create component
     */
    private Component createComponent(final ParserRuleContext ctx,
            final OOPSourceModelConstants.JavaComponentTypes componentType) {
        final Component newCmp = new Component();
        newCmp.setCode(AntlrUtil.getFormattedText(ctx));
        newCmp.setPackageName(currentPkg);
        newCmp.setComponentType(OOPSourceModelConstants.getJavaComponentTypes().get(componentType));
        newCmp.setComment(AntlrUtil.getContextMultiLineComment(ctx, currFileSourceCode,
                JAVA_BLOCK_COMMENT_BEGIN_SYMBOL, JAVA_BLOCK_COMMENT_END_SYMBOL));
        newCmp.setStartLine(ctx.getStart().getLine());
        newCmp.setEndLine(ctx.getStop().getLine());
        return newCmp;
    }

    @Override
    public final void enterPackageDeclaration(final JavaParser.PackageDeclarationContext ctx) {
        currentPkg = ctx.qualifiedName().getText();
        componentCompletionMultiplier = 1;
        currentImports.clear();
        if (!componentStack.isEmpty()) {
            System.out
            .println("Clarity Java Listener found new package declaration while component stack not empty! component stack size is: "
                    + componentStack.size());
        }
        componentStack.clear();
    }

    @Override
    public final void enterImportDeclaration(final JavaParser.ImportDeclarationContext ctx) {
        final String fullImportName = ctx.qualifiedName().getText();
        final String[] bits = fullImportName.split(Pattern.quote("."));
        final String shortImportName = bits[(bits.length - 1)];
        currentImports.add(fullImportName);
        currentImportsMap.put(shortImportName, fullImportName);
        if (currentPkg.isEmpty()) {
            currentPkg = "default";
        }
    }

    @Override
    public final void enterAnnotationTypeDeclaration(final AnnotationTypeDeclarationContext ctx) {

        ignoreTreeWalk = true;
    }

    @Override
    public final void exitAnnotationTypeDeclaration(final AnnotationTypeDeclarationContext ctx) {

        ignoreTreeWalk = false;
    }

    @Override
    public final void enterClassDeclaration(final JavaParser.ClassDeclarationContext ctx) {
        if (!ignoreTreeWalk) {
            final Component classCmp = createComponent(ctx, OOPSourceModelConstants.JavaComponentTypes.CLASS_COMPONENT);
            classCmp.setCode(currFileSourceCode);
            classCmp.setComponentName(generateComponentName(ctx.Identifier().getText()));
            classCmp.setImports(currentImports);
            if (ctx.type() != null) {
                classCmp.addSuperClass(resolveType(ctx.type().getText()));
                classCmp.insertExternalClassType(new TypeReference(resolveType(ctx.type().getText()), ctx.getStart()
                        .getLine()));
            }
            componentStack.push(classCmp);
        }
    }

    /**
     * Returns corresponding import statement based on given type.
     *
     * @param type
     *            type to resolve
     * @return full name of the given type
     */
    private String resolveType(final String type) {

        if (!ignoreTreeWalk) {
            if (currentImportsMap.containsKey(type)) {
                return currentImportsMap.get(type);
            }
            if (type.contains(".")) {
                return type;
            }
            if (OOPSourceModelConstants.getJavaDefaultClasses().containsKey(type)) {
                return OOPSourceModelConstants.getJavaDefaultClasses().get(type);
            }
        }
        return currentPkg + "." + type;
    }

    @Override
    public final void exitClassDeclaration(final JavaParser.ClassDeclarationContext ctx) {
        if (!ignoreTreeWalk) {

            completeComponent();
        }
    }

    @Override
    public final void enterEnumDeclaration(final JavaParser.EnumDeclarationContext ctx) {
        if (!ignoreTreeWalk) {
            final Component enumCmp = createComponent(ctx, OOPSourceModelConstants.JavaComponentTypes.ENUM_COMPONENT);
            enumCmp.setCode(currFileSourceCode);
            enumCmp.setComponentName(generateComponentName(ctx.Identifier().getText()));
            enumCmp.setImports(currentImports);

            componentStack.push(enumCmp);
        }
    }

    @Override
    public final void exitEnumDeclaration(final JavaParser.EnumDeclarationContext ctx) {
        if (!ignoreTreeWalk) {
            completeComponent();
        }
    }

    @Override
    public final void enterEnumConstant(final JavaParser.EnumConstantContext ctx) {
        if (!ignoreTreeWalk) {
            final Component enumConstCmp = createComponent(ctx,
                    OOPSourceModelConstants.JavaComponentTypes.ENUM_CONSTANT_COMPONENT);
            enumConstCmp.setCode(AntlrUtil.getFormattedText(ctx));
            enumConstCmp.setComponentName(generateComponentName(ctx.Identifier().getText()));

            componentStack.push(enumConstCmp);
        }
    }

    @Override
    public final void exitEnumConstant(final JavaParser.EnumConstantContext ctx) {
        if (!ignoreTreeWalk) {
            completeComponent();
        }
    }

    @Override
    public final void enterInterfaceDeclaration(final JavaParser.InterfaceDeclarationContext ctx) {
        if (!ignoreTreeWalk) {
            final Component interfaceCmp = createComponent(ctx,
                    OOPSourceModelConstants.JavaComponentTypes.INTERFACE_COMPONENT);
            interfaceCmp.setCode(AntlrUtil.getFormattedText(ctx));
            interfaceCmp.setComponentName(generateComponentName(ctx.Identifier().getText()));
            interfaceCmp.setImports(currentImports);

            componentStack.push(interfaceCmp);
        }
    }

    @Override
    public final void exitInterfaceDeclaration(final JavaParser.InterfaceDeclarationContext ctx) {
        if (!ignoreTreeWalk) {
            completeComponent();
        }
    }

    @Override
    public final void enterMethodDeclaration(final JavaParser.MethodDeclarationContext ctx) {
        if (!ignoreTreeWalk) {
            final Component currMethodCmp = createComponent(ctx,
                    OOPSourceModelConstants.JavaComponentTypes.METHOD_COMPONENT);
            currMethodCmp.setCode(AntlrUtil.getFormattedText(ctx));
            currMethodCmp.setComponentName(generateComponentName(ctx.Identifier().getText()));

            componentStack.push(currMethodCmp);
        }
    }

    @Override
    public final void enterInterfaceMethodDeclaration(final JavaParser.InterfaceMethodDeclarationContext ctx) {
        if (!ignoreTreeWalk) {
            final Component currMethodCmp = createComponent(ctx,
                    OOPSourceModelConstants.JavaComponentTypes.METHOD_COMPONENT);

            currMethodCmp.setComponentName(generateComponentName(ctx.Identifier().getText()));

            componentStack.push(currMethodCmp);
        }
    }

    @Override
    public final void enterConstructorDeclaration(final JavaParser.ConstructorDeclarationContext ctx) {
        if (!ignoreTreeWalk) {
            final Component currMethodCmp = createComponent(ctx,
                    OOPSourceModelConstants.JavaComponentTypes.CONSTRUCTOR_COMPONENT);
            currMethodCmp.setCode(AntlrUtil.getFormattedText(ctx));
            currMethodCmp.setComponentName(generateComponentName(ctx.Identifier().getText()));

            componentStack.push(currMethodCmp);
        }
    }

    @Override
    public final void exitMethodDeclaration(final JavaParser.MethodDeclarationContext ctx) {
        if (!ignoreTreeWalk) {

            completeComponent();
        }
    }

    @Override
    public final void exitInterfaceMethodDeclaration(final JavaParser.InterfaceMethodDeclarationContext ctx) {
        if (!ignoreTreeWalk) {
            completeComponent();
        }
    }

    @Override
    public final void exitConstructorDeclaration(final JavaParser.ConstructorDeclarationContext ctx) {
        if (!ignoreTreeWalk) {
            completeComponent();
        }
    }

    @Override
    public final void enterQualifiedNameList(final JavaParser.QualifiedNameListContext ctx) {
        if (!ignoreTreeWalk) {
            final Component currMethodCmp = componentStack.pop();
            for (final JavaParser.QualifiedNameContext qctx : ctx.qualifiedName()) {
                currMethodCmp.insertException(resolveType(qctx.getText()));
                currMethodCmp.insertExternalClassType(new TypeReference(resolveType(qctx.getText()), ctx.getStart()
                        .getLine()));
            }
            componentStack.push(currMethodCmp);
        }
    }

    @Override
    public final void enterFormalParameter(final JavaParser.FormalParameterContext ctx) {
        if (!ignoreTreeWalk) {
            final Component currMethodCmp = componentStack.peek();
            if (currMethodCmp.getComponentType().equals(
                    OOPSourceModelConstants.getJavaComponentTypes().get(
                            OOPSourceModelConstants.JavaComponentTypes.CONSTRUCTOR_COMPONENT))) {
                final Component cmp = createComponent(ctx,
                        OOPSourceModelConstants.JavaComponentTypes.CONSTRUCTOR_PARAMETER_COMPONENT);
                cmp.setCode(AntlrUtil.getFormattedText(ctx));
                componentStack.push(cmp);
            } else {
                final Component cmp = createComponent(ctx,
                        OOPSourceModelConstants.JavaComponentTypes.METHOD_PARAMETER_COMPONENT);
                cmp.setCode(AntlrUtil.getFormattedText(ctx));
                componentStack.push(cmp);
            }
        }
    }

    @Override
    public final void exitFormalParameter(final JavaParser.FormalParameterContext ctx) {
        if (!ignoreTreeWalk) {
            completeComponent();
        }
    }

    @Override
    public final void enterLocalVariableDeclaration(final JavaParser.LocalVariableDeclarationContext ctx) {
        if (!ignoreTreeWalk) {
            final Component cmp = createComponent(ctx,
                    OOPSourceModelConstants.JavaComponentTypes.LOCAL_VARIABLE_COMPONENT);
            cmp.setCode(AntlrUtil.getFormattedText(ctx));
            componentStack.push(cmp);
        }
    }

    @Override
    public final void exitLocalVariableDeclaration(final JavaParser.LocalVariableDeclarationContext ctx) {
        if (!ignoreTreeWalk) {
            completeComponent();
        }
    }

    @Override
    public final void enterFieldDeclaration(final JavaParser.FieldDeclarationContext ctx) {
        if (!ignoreTreeWalk) {
            final Component currCmp = componentStack.peek();
            if (currCmp.getComponentType().equals(
                    OOPSourceModelConstants.getJavaComponentTypes().get(
                            OOPSourceModelConstants.JavaComponentTypes.INTERFACE_COMPONENT))) {
                final Component cmp = createComponent(ctx,
                        OOPSourceModelConstants.JavaComponentTypes.INTERFACE_CONSTANT_COMPONENT);
                cmp.setCode(AntlrUtil.getFormattedText(ctx));
                componentStack.push(cmp);
            } else {
                final Component cmp = createComponent(ctx, OOPSourceModelConstants.JavaComponentTypes.FIELD_COMPONENT);
                cmp.setCode(AntlrUtil.getFormattedText(ctx));
                componentStack.push(cmp);
            }
        }
    }

    @Override
    public final void exitFieldDeclaration(final JavaParser.FieldDeclarationContext ctx) {
        if (!ignoreTreeWalk) {
            completeComponent();
        }
    }

    @Override
    public final void enterTypeList(final JavaParser.TypeListContext ctx) {
        if (!ignoreTreeWalk) {
            final Component currCmp = componentStack.pop();
            for (final JavaParser.TypeContext tempType : ctx.type()) {
                currCmp.addImplementedClass(resolveType(tempType.getText()));
                currCmp.insertExternalClassType(new TypeReference(resolveType(tempType.getText()), ctx.getStart()
                        .getLine()));
            }
            componentStack.push(currCmp);
        }
    }

    @Override
    public final void enterTypeParameters(final JavaParser.TypeParametersContext ctx) {
        if (!ignoreTreeWalk) {
            final Component currCmp = componentStack.pop();

            currCmp.setDeclarationTypeSnippet(AntlrUtil.getFormattedText(ctx));

            componentStack.push(currCmp);
        }
    }

    @Override
    public final void enterAnnotation(final JavaParser.AnnotationContext ctx) {
        if (!ignoreTreeWalk) {

            final Component currCmp = componentStack.pop();

            final HashMap<String, String> elementValuePairs = new HashMap<String, String>();
            if (ctx.elementValuePairs() != null) {
                for (final JavaParser.ElementValuePairContext evctx : ctx.elementValuePairs().elementValuePair()) {
                    elementValuePairs.put(evctx.Identifier().getText(), evctx.elementValue().getText());
                }
            }
            if (ctx.elementValue() != null) {
                elementValuePairs.put(ctx.elementValue().getText(), "");
            }
            currCmp.insertAnnotation(new SimpleEntry<String, HashMap<String, String>>(ctx.annotationName().getText(),
                    elementValuePairs));

            componentStack.push(currCmp);
        }
    }

    @Override
    public final void enterClassOrInterfaceType(final JavaParser.ClassOrInterfaceTypeContext ctx) {
        if (!ignoreTreeWalk) {
            final Component currCmp = componentStack.pop();

            String type = "";
            for (final TerminalNode ciftx : ctx.Identifier()) {
                type = type + ciftx.getText() + ".";
            }
            type = type.substring(0, type.length() - 1);
            currCmp.insertExternalClassType(new TypeReference(resolveType(type), ctx.getStart().getLine()));
            componentStack.push(currCmp);
        }
    }

    @Override
    public final void enterType(final JavaParser.TypeContext ctx) {
        if (!ignoreTreeWalk) {
            final Component currCmp = componentStack.pop();
            if ((currCmp.getDeclarationTypeSnippet() == null) && (!currCmp.isBaseComponent())) {
                currCmp.setDeclarationTypeSnippet(ctx.getText());
            }
            componentStack.push(currCmp);
        }
    }

    @Override
    public final void enterPrimitiveType(final JavaParser.PrimitiveTypeContext ctx) {
        if (!ignoreTreeWalk) {
            final Component currCmp = componentStack.pop();

            currCmp.insertExternalClassType(new TypeReference(resolveType(ctx.getText()), ctx.getStart().getLine()));

            componentStack.push(currCmp);
        }
    }

    @Override
    public final void enterRegularModifier(final JavaParser.RegularModifierContext ctx) {
        if (!ignoreTreeWalk) {
            final Component currCmp = componentStack.pop();

            currCmp.insertAccessModifier(ctx.getText());

            componentStack.push(currCmp);
        }
    }

    @Override
    public final void enterPrimary(final JavaParser.PrimaryContext ctx) {
        if (!ignoreTreeWalk) {
            final Component currCmp = componentStack.pop();
            if (ctx.Identifier() != null) {
                currCmp.insertExternalClassType(new TypeReference(resolveType(ctx.getText()), ctx.getStart().getLine()));
            }
            componentStack.push(currCmp);
        }
    }

    @Override
    public final void enterVariableDeclaratorId(final JavaParser.VariableDeclaratorIdContext ctx) {
        if (!ignoreTreeWalk) {
            final Component currCmp = componentStack.pop();
            if ((currCmp.getComponentName() == null) || (currCmp.getComponentName().isEmpty())) {
                currCmp.setComponentName(generateComponentName(ctx.Identifier().getText()));
            } else {
                final Component copyCmp = new Component(currCmp);
                componentCompletionMultiplier += 1;
                copyCmp.setComponentName(generateComponentName(ctx.Identifier().getText()));
                componentStack.push(copyCmp);
            }
            componentStack.push(currCmp);
        }
    }

    @Override
    public final void enterVariableInitializer(final JavaParser.VariableInitializerContext ctx) {
        if (!ignoreTreeWalk) {
            final Component currCmp = componentStack.pop();
            currCmp.setValue(ctx.getText());

            componentStack.push(currCmp);
        }
    }

    @Override
    public final void enterCompilationUnit(final JavaParser.CompilationUnitContext ctx) {
        currFileSourceCode = AntlrUtil.getFormattedText(ctx);
    }
}