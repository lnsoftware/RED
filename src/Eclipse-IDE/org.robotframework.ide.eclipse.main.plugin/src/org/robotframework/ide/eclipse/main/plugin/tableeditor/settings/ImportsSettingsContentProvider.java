package org.robotframework.ide.eclipse.main.plugin.tableeditor.settings;

import java.util.Arrays;
import java.util.List;

import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.Viewer;
import org.robotframework.ide.eclipse.main.plugin.tableeditor.ElementAddingToken;

public class ImportsSettingsContentProvider implements IStructuredContentProvider {

    private ElementAddingToken elementAddingToken;
    private final boolean editable;

    public ImportsSettingsContentProvider(final boolean editable) {
        this.editable = editable;
    }

    @Override
    public void dispose() {
        if (elementAddingToken != null) {
            elementAddingToken.dispose();
        }
    }

    @Override
    public void inputChanged(final Viewer viewer, final Object oldInput, final Object newInput) {
        // nothing to do
    }

    @Override
    public Object[] getElements(final Object inputElement) {
        final Object[] elements = ((List<?>) inputElement).toArray();
        final Object[] newElements = Arrays.copyOf(elements, elements.length + 1, Object[].class);
        elementAddingToken = new ElementAddingToken("import", editable);
        newElements[elements.length] = elementAddingToken;
        return newElements;
    }
}
