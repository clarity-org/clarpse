package com.clarity.invocation;

import java.io.Serializable;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class AnnotationInvocation extends ComponentInvocation implements Serializable {

    private static final long serialVersionUID = 4146299386492074733L;
    @SuppressWarnings("unused")
    private final String type = "annotation";
    private List<Entry<String, HashMap<String, String>>> annotations = new ArrayList<Map.Entry<String, HashMap<String, String>>>();

    public List<Entry<String, HashMap<String, String>>> annotations() {
        return annotations;
    }

    public AnnotationInvocation(final String invocationComponentName,
            final SimpleEntry<String, HashMap<String, String>> annotation) {
        super(invocationComponentName);
        annotations.add(annotation);
    }

    public AnnotationInvocation() {
        super();
    }

    public AnnotationInvocation(String invokedComponent, List<Entry<String, HashMap<String, String>>> annotations2) {
        super(invokedComponent);
        annotations = annotations2;
    }

    @Override
    public boolean empty() {
        return (super.empty() && annotations.isEmpty());
    }

    @Override
    public Object clone() {
        return new AnnotationInvocation(invokedComponent(), annotations);
    }
}
