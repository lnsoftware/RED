/*
 * Copyright 2016 Nokia Solutions and Networks
 * Licensed under the Apache License, Version 2.0,
 * see license.txt file for details.
 */
package org.rf.ide.core.testdata.text.write.tables;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.rf.ide.core.testdata.mapping.table.ElementsUtility;
import org.rf.ide.core.testdata.model.AModelElement;
import org.rf.ide.core.testdata.model.FilePosition;
import org.rf.ide.core.testdata.model.ModelType;
import org.rf.ide.core.testdata.model.RobotFile;
import org.rf.ide.core.testdata.model.table.ARobotSectionTable;
import org.rf.ide.core.testdata.model.table.IExecutableStepsHolder;
import org.rf.ide.core.testdata.model.table.RobotElementsComparatorWithPositionChangedPresave;
import org.rf.ide.core.testdata.model.table.TableHeader;
import org.rf.ide.core.testdata.text.read.EndOfLineBuilder.EndOfLineTypes;
import org.rf.ide.core.testdata.text.read.IRobotLineElement;
import org.rf.ide.core.testdata.text.read.IRobotTokenType;
import org.rf.ide.core.testdata.text.read.RobotLine;
import org.rf.ide.core.testdata.text.read.recognizer.RobotToken;
import org.rf.ide.core.testdata.text.read.recognizer.RobotTokenType;
import org.rf.ide.core.testdata.text.read.separators.Separator;
import org.rf.ide.core.testdata.text.write.DumperHelper;
import org.rf.ide.core.testdata.text.write.SectionBuilder.Section;

import com.google.common.base.Optional;

public abstract class AExecutableTableElementDumper implements IExecutableSectionElementDumper {

    private final DumperHelper aDumpHelper;

    private final ElementsUtility anElementHelper;

    private final ModelType servedType;

    private final TableElementDumperHelper elemDumperHelper;

    private final List<IForceFixBeforeDumpTask> afterSortTasks = new ArrayList<>(0);

    public AExecutableTableElementDumper(final DumperHelper aDumpHelper, final ModelType servedType) {
        this.aDumpHelper = aDumpHelper;
        this.anElementHelper = new ElementsUtility();
        this.servedType = servedType;
        this.elemDumperHelper = new TableElementDumperHelper();
    }

    protected void addAfterSortTask(final IForceFixBeforeDumpTask fixTask) {
        this.afterSortTasks.add(fixTask);
    }

    protected DumperHelper getDumperHelper() {
        return this.aDumpHelper;
    }

    protected ElementsUtility getElementHelper() {
        return this.anElementHelper;
    }

    protected TableElementDumperHelper getElementDumperHelper() {
        return this.elemDumperHelper;
    }

    @Override
    public boolean isServedType(final AModelElement<? extends IExecutableStepsHolder<?>> element) {
        return (element.getModelType() == servedType);
    }

    public abstract RobotElementsComparatorWithPositionChangedPresave getSorter(
            final AModelElement<? extends IExecutableStepsHolder<?>> currentElement);

    @Override
    public void dump(final RobotFile model, final List<Section> sections, final int sectionWithHeaderPos,
            final TableHeader<? extends ARobotSectionTable> th,
            final List<AModelElement<? extends IExecutableStepsHolder<?>>> sortedSettings,
            final AModelElement<? extends IExecutableStepsHolder<?>> currentElement, final List<RobotLine> lines) {
        final RobotToken elemDeclaration = currentElement.getDeclaration();
        final FilePosition filePosition = elemDeclaration.getFilePosition();
        int fileOffset = -1;
        if (filePosition != null && !filePosition.isNotSet()) {
            fileOffset = filePosition.getOffset();
        }

        RobotLine currentLine = null;
        if (fileOffset >= 0) {
            Optional<Integer> lineIndex = model.getRobotLineIndexBy(fileOffset);
            if (lineIndex.isPresent()) {
                currentLine = model.getFileContent().get(lineIndex.get());
            }
        }

        if (!lines.isEmpty()) {
            if (lines.get(lines.size() - 1).getEndOfLine().getTypes().contains(EndOfLineTypes.EOF)) {
                lines.get(lines.size() - 1).setEndOfLine(null, -1, -1);
            }
        }

        IRobotLineElement lastToken = elemDeclaration;
        if (currentLine != null) {
            getDumperHelper().getSeparatorDumpHelper().dumpSeparatorsBeforeToken(model, currentLine, elemDeclaration,
                    lines);
        }

        final RobotElementsComparatorWithPositionChangedPresave sorter = getSorter(currentElement);
        final List<RobotToken> tokens = sorter.getTokensInElement();

        Collections.sort(tokens, sorter);
        for (final IForceFixBeforeDumpTask task : afterSortTasks) {
            task.fixBeforeDump(currentElement, tokens);
        }

        if (getDumperHelper().isCurrentFileDirty() && !lines.isEmpty()
                && !getDumperHelper().getEmptyLineDumper().isEmptyLine(lines.get(lines.size() - 1))
                && (!lastToken.getFilePosition().isNotSet()
                        && !getElementDumperHelper().getFirstBrokenChainPosition(tokens, true).isPresent()
                        && !tokens.isEmpty() && !getElementDumperHelper().isDirtyAnyDirtyInside(tokens))) {
            getDumperHelper().getDumpLineUpdater().updateLine(model, lines, getDumperHelper().getLineSeparator(model));
        }

        int nrOfTokens = getElementDumperHelper().getLastIndexNotEmptyIndex(tokens) + 1;

        if (!elemDeclaration.isDirty() && currentLine != null) {
            getDumperHelper().getDumpLineUpdater().updateLine(model, lines, elemDeclaration);
            final List<IRobotLineElement> lineElements = currentLine.getLineElements();
            final int tokenPosIndex = lineElements.indexOf(elemDeclaration);
            if (lineElements.size() - 1 > tokenPosIndex + 1) {
                for (int index = tokenPosIndex + 1; index < lineElements.size(); index++) {
                    final IRobotLineElement nextElem = lineElements.get(index);
                    final List<IRobotTokenType> types = nextElem.getTypes();
                    if (types.contains(RobotTokenType.PRETTY_ALIGN_SPACE)
                            || types.contains(RobotTokenType.ASSIGNMENT)) {
                        getDumperHelper().getDumpLineUpdater().updateLine(model, lines, nextElem);
                        lastToken = nextElem;
                    } else {
                        break;
                    }
                }
            }
        } else {
            boolean shouldDumpDeclaration = true;
            if (!elemDeclaration.isNotEmpty()) {
                if (tokens.size() > 1) {
                    final RobotToken theNextToken = tokens.get(1);
                    final List<IRobotTokenType> types = theNextToken.getTypes();
                    if (types.contains(RobotTokenType.COMMENT_CONTINUE)
                            || types.contains(RobotTokenType.START_HASH_COMMENT)) {
                        shouldDumpDeclaration = false;
                    }
                }
            }

            if (shouldDumpDeclaration) {
                boolean wasPrettyAlign = false;
                if (!lines.isEmpty()) {
                    final RobotLine lastLine = lines.get(lines.size() - 1);
                    if (getDumperHelper().getEmptyLineDumper().isEmptyLine(lastLine)) {
                        final List<IRobotLineElement> lineElements = lastLine.getLineElements();
                        if (!lineElements.isEmpty()) {
                            final IRobotLineElement lastElement = lineElements.get(lineElements.size() - 1);
                            if (lastElement.getTypes().contains(RobotTokenType.PRETTY_ALIGN_SPACE)) {
                                wasPrettyAlign = (lastElement.getStartOffset()
                                        + lastElement.getText().length()) == lastToken.getStartOffset();
                                if (!wasPrettyAlign && lastElement.getLineNumber() != lastToken.getLineNumber()) {
                                    wasPrettyAlign = true;
                                }
                            }
                        }
                    } else {
                        getDumperHelper().getDumpLineUpdater().updateLine(model, lines,
                                getDumperHelper().getLineSeparator(model));
                    }
                }

                if (getDumperHelper().isSeparatorForExecutableUnitName(
                        getDumperHelper().getSeparator(model, lines, lastToken, lastToken))) {
                    int countSeparatorsBefore = getDumperHelper().countSeparatorsBefore(lines);
                    if (countSeparatorsBefore == 0) {
                        Separator beforeExecRowSep = getDumperHelper().getSeparator(model, lines, lastToken, lastToken);
                        if (beforeExecRowSep.getText().equals(" | ")) {
                            beforeExecRowSep.setText("| ");
                            beforeExecRowSep.setRaw("| ");
                        }
                        getDumperHelper().getDumpLineUpdater().updateLine(model, lines, beforeExecRowSep);
                        ++countSeparatorsBefore;
                    }

                    if (countSeparatorsBefore == 1) {
                        getDumperHelper().getDumpLineUpdater().updateLine(model, lines,
                                getDumperHelper().getSeparator(model, lines, lastToken, lastToken));
                    }
                }

                if (!getDumperHelper().wasSeparatorBefore(lines) && !wasPrettyAlign) {
                    getDumperHelper().getDumpLineUpdater().updateLine(model, lines,
                            getDumperHelper().getSeparator(model, lines, lastToken, lastToken));
                }

                getDumperHelper().getDumpLineUpdater().updateLine(model, lines, elemDeclaration);
            }
            lastToken = elemDeclaration;
        }

        // dump as it is
        if (!lastToken.getFilePosition().isNotSet()
                && !getElementDumperHelper().getFirstBrokenChainPosition(tokens, true).isPresent() && !tokens.isEmpty()
                && !getElementDumperHelper().isDirtyAnyDirtyInside(tokens)) {
            boolean wasDumped = getElementDumperHelper().dumpAsItIs(getDumperHelper(), model, lastToken, tokens, lines);
            if (wasDumped) {
                return;
            }
        }

        final List<Integer> lineEndPos = new ArrayList<>(getElementDumperHelper().getLineEndPos(model, tokens));

        // just dump now
        if (tokens.size() > 1 && lineEndPos.contains(0)) {
            if (currentLine != null) {
                getDumperHelper().getDumpLineUpdater().updateLine(model, lines, currentLine.getEndOfLine());
            }

            if (getDumperHelper().isSeparatorForExecutableUnitName(
                    getDumperHelper().getSeparator(model, lines, lastToken, lastToken))) {
                int countSeparatorsBefore = getDumperHelper().countSeparatorsBefore(lines);
                Separator beforeExecRowSep = null;
                if (countSeparatorsBefore == 0) {
                    beforeExecRowSep = getDumperHelper().getSeparator(model, lines, lastToken, lastToken);
                    if (beforeExecRowSep.getText().equals(" | ")) {
                        beforeExecRowSep.setText("| ");
                        beforeExecRowSep.setRaw("| ");
                    }
                    getDumperHelper().getDumpLineUpdater().updateLine(model, lines, beforeExecRowSep);

                    ++countSeparatorsBefore;
                }

                if (countSeparatorsBefore == 1) {
                    Separator separator;
                    if (beforeExecRowSep == null) {
                        separator = getDumperHelper().getSeparator(model, lines, lastToken, lastToken);
                    } else {
                        separator = getDumperHelper().getSeparator(model, lines, beforeExecRowSep, beforeExecRowSep);
                    }

                    getDumperHelper().getDumpLineUpdater().updateLine(model, lines, separator);
                }
            } else {
                Separator sep = getDumperHelper().getSeparator(model, lines, lastToken, tokens.get(0));
                getDumperHelper().getDumpLineUpdater().updateLine(model, lines, sep);
            }

            RobotToken lineContinueToken = new RobotToken();
            lineContinueToken.setRaw("...");
            lineContinueToken.setText("...");
            lineContinueToken.setType(RobotTokenType.PREVIOUS_LINE_CONTINUE);

            getDumperHelper().getDumpLineUpdater().updateLine(model, lines, lineContinueToken);
        }

        for (int tokenId = 0; tokenId < nrOfTokens; tokenId++) {
            final IRobotLineElement tokElem = tokens.get(tokenId);
            if (tokenId == 0 && (tokElem == lastToken || lastToken.getTypes().contains(RobotTokenType.ASSIGNMENT)
                    || lastToken.getTypes().contains(RobotTokenType.PRETTY_ALIGN_SPACE))) {
                lastToken = tokElem;
                continue;
            }

            if (tokElem.getText().equals("\n...")) {
                getDumperHelper().getDumpLineUpdater().updateLine(model, lines,
                        getDumperHelper().getLineSeparator(model));

                RobotToken lineContinueToken = new RobotToken();
                lineContinueToken.setRaw("...");
                lineContinueToken.setText("...");
                lineContinueToken.setType(RobotTokenType.PREVIOUS_LINE_CONTINUE);

                getDumperHelper().getDumpLineUpdater().updateLine(model, lines,
                        getDumperHelper().getSeparator(model, lines, lastToken, lineContinueToken));

                getDumperHelper().getDumpLineUpdater().updateLine(model, lines, lineContinueToken);

                getDumperHelper().getDumpLineUpdater().updateLine(model, lines,
                        getDumperHelper().getSeparator(model, lines, lastToken, lineContinueToken));

                lastToken = tokElem;
                continue;
            }

            if (!getDumperHelper().wasSeparatorBefore(lines)) {
                Separator sep = getDumperHelper().getSeparator(model, lines, lastToken, tokElem);
                getDumperHelper().getDumpLineUpdater().updateLine(model, lines, sep);
                lastToken = sep;
            }

            getDumperHelper().getDumpLineUpdater().updateLine(model, lines, tokElem);
            lastToken = tokElem;

            RobotLine currentLineTok = null;
            if (!tokElem.getFilePosition().isNotSet()) {
                currentLineTok = null;
                if (fileOffset >= 0) {
                    Optional<Integer> lineIndex = model.getRobotLineIndexBy(tokElem.getFilePosition().getOffset());
                    if (lineIndex.isPresent()) {
                        currentLineTok = model.getFileContent().get(lineIndex.get());
                    }
                }

                if (currentLineTok != null && !tokElem.isDirty()) {
                    List<IRobotLineElement> lineElements = currentLineTok.getLineElements();
                    int thisTokenPosIndex = lineElements.indexOf(tokElem);
                    if (thisTokenPosIndex >= 0) {
                        if (lineElements.size() - 1 > thisTokenPosIndex + 1) {
                            final IRobotLineElement nextElem = lineElements.get(thisTokenPosIndex + 1);
                            if (nextElem.getTypes().contains(RobotTokenType.PRETTY_ALIGN_SPACE)
                                    || nextElem.getTypes().contains(RobotTokenType.ASSIGNMENT)) {
                                getDumperHelper().getDumpLineUpdater().updateLine(model, lines, nextElem);
                                lastToken = nextElem;
                            }
                        }
                    }
                }
            }

            boolean dumpAfterSep = false;
            if (tokenId + 1 < nrOfTokens) {
                if (!tokElem.getTypes().contains(RobotTokenType.START_HASH_COMMENT)
                        && !tokElem.getTypes().contains(RobotTokenType.COMMENT_CONTINUE)) {
                    IRobotLineElement nextElem = tokens.get(tokenId + 1);
                    if (nextElem.getTypes().contains(RobotTokenType.START_HASH_COMMENT)
                            || nextElem.getTypes().contains(RobotTokenType.COMMENT_CONTINUE)) {
                        dumpAfterSep = true;
                    }
                }
            } else {
                dumpAfterSep = true;
            }

            if (dumpAfterSep && currentLine != null) {
                getDumperHelper().getSeparatorDumpHelper().dumpSeparatorsAfterToken(model, currentLine, lastToken,
                        lines);
            }

            // check if is not end of line
            if (lineEndPos.contains(tokenId) && tokenId + 1 < nrOfTokens) {
                if (currentLine != null) {
                    getDumperHelper().getDumpLineUpdater().updateLine(model, lines, currentLine.getEndOfLine());
                } else {
                    // new end of line
                }

                if (!tokens.isEmpty()) {
                    Separator sepNew = getDumperHelper().getSeparator(model, lines, lastToken, tokens.get(tokenId + 1));
                    getDumperHelper().getDumpLineUpdater().updateLine(model, lines, sepNew);

                    RobotToken lineContinueToken = new RobotToken();
                    lineContinueToken.setRaw("...");
                    lineContinueToken.setText("...");
                    lineContinueToken.setType(RobotTokenType.PREVIOUS_LINE_CONTINUE);

                    getDumperHelper().getDumpLineUpdater().updateLine(model, lines, lineContinueToken);

                }
            }
        }
    }
}
