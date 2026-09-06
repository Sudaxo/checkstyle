///////////////////////////////////////////////////////////////////////////////////////////////
// checkstyle: Checks Java source code and other text files for adherence to a set of rules.
// Copyright (C) 2001-2026 the original author or authors.
//
// This library is free software; you can redistribute it and/or
// modify it under the terms of the GNU Lesser General Public
// License as published by the Free Software Foundation; either
// version 2.1 of the License, or (at your option) any later version.
//
// This library is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
// Lesser General Public License for more details.
//
// You should have received a copy of the GNU Lesser General Public
// License along with this library; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
///////////////////////////////////////////////////////////////////////////////////////////////

package com.puppycrawl.tools.checkstyle.checks.imports;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.puppycrawl.tools.checkstyle.FileStatefulCheck;
import com.puppycrawl.tools.checkstyle.api.AbstractCheck;
import com.puppycrawl.tools.checkstyle.api.DetailAST;
import com.puppycrawl.tools.checkstyle.api.FullIdent;
import com.puppycrawl.tools.checkstyle.api.TokenTypes;

/**
 * <div>
 * Checks for redundant import statements. An import statement is
 * considered redundant if:
 * </div>
 * <ul>
 *   <li>It is a duplicate of another import. This is, when a class or a module
 *   is imported more than once.</li>
 *   <li>The class non-statically imported is from the {@code java.lang}
 *   package, e.g. importing {@code java.lang.String}.</li>
 *   <li>The class non-statically imported is from the same package as the
 *   current package.</li>
 * </ul>
 *
 * @since 3.0
 */
@FileStatefulCheck
public class RedundantImportCheck
    extends AbstractCheck {

    /**
     * A key is pointing to the warning message text in "messages.properties"
     * file.
     */
    public static final String MSG_LANG = "import.lang";

    /**
     * A key is pointing to the warning message text in "messages.properties"
     * file.
     */
    public static final String MSG_SAME = "import.same";

    /**
     * A key is pointing to the warning message text in "messages.properties"
     * file.
     */
    public static final String MSG_DUPLICATE = "import.duplicate";

    /** Imports indexed by their text. */
    private final Map<String, List<FullIdent>> imports = new HashMap<>();
    /** Static and module imports indexed by their text. */
    private final Map<String, List<FullIdent>> staticAndModuleImports = new HashMap<>();

    /** Name of package in file. */
    private String pkgName;

    /**
     * Creates a new {@code RedundantImportCheck} instance.
     */
    public RedundantImportCheck() {
        // no code by default
    }

    @Override
    public void beginTree(DetailAST aRootAST) {
        pkgName = null;
        imports.clear();
        staticAndModuleImports.clear();
    }

    @Override
    public int[] getDefaultTokens() {
        return getRequiredTokens();
    }

    @Override
    public int[] getAcceptableTokens() {
        return getRequiredTokens();
    }

    @Override
    public int[] getRequiredTokens() {
        return new int[] {
            TokenTypes.IMPORT,
            TokenTypes.STATIC_IMPORT,
            TokenTypes.PACKAGE_DEF,
            TokenTypes.MODULE_IMPORT,
        };
    }

    @Override
    public void visitToken(DetailAST ast) {
        if (ast.getType() == TokenTypes.PACKAGE_DEF) {
            pkgName = FullIdent.createFullIdent(
                    ast.getLastChild().getPreviousSibling()).getText();
        }
        else if (ast.getType() == TokenTypes.IMPORT) {
            final FullIdent imp = FullIdent.createFullIdentBelow(ast);
            final String importText = imp.getText();
            if (isFromPackage(importText, "java.lang")) {
                log(ast, MSG_LANG, importText);
            }
            // imports from unnamed package are not allowed,
            // so we are checking SAME rule only for named packages
            else if (pkgName != null && isFromPackage(importText, pkgName)) {
                log(ast, MSG_SAME, importText);
            }
            // Check for a duplicate import
            final List<FullIdent> matchingImports = imports.get(importText);
            if (matchingImports != null) {
                matchingImports.forEach(full -> log(ast, MSG_DUPLICATE,
                        full.getLineNo(), importText));
            }
            imports.computeIfAbsent(importText, key -> new ArrayList<>()).add(imp);
        }
        else {
            // Check for a duplicate static or module import
            final DetailAST identNode = ast.getLastChild().getPreviousSibling();
            final FullIdent importFullIdent = FullIdent.createFullIdent(identNode);
            final String importText = importFullIdent.getText();

            final List<FullIdent> matchingImports = staticAndModuleImports.get(importText);
            if (matchingImports != null) {
                matchingImports.forEach(existingImport -> log(ast, MSG_DUPLICATE,
                        existingImport.getLineNo(), importText));
            }
            staticAndModuleImports.computeIfAbsent(importText, key -> new ArrayList<>())
                    .add(importFullIdent);
        }
    }

    /**
     * Determines if an import statement is for types from a specified package.
     *
     * @param importName the import name
     * @param pkg the package name
     * @return whether from the package
     */
    private static boolean isFromPackage(String importName, String pkg) {
        // imports from unnamed package are not allowed:
        // https://docs.oracle.com/javase/specs/jls/se7/html/jls-7.html#jls-7.5
        // So '.' must be present in member name and we are not checking for it
        final int index = importName.lastIndexOf('.');
        final String front = importName.substring(0, index);
        return pkg.equals(front);
    }

}
