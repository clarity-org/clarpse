package com.clarity.invocation;

import java.io.Serializable;

import com.clarity.EmptyResource;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Represents an invocation of another component in the code base.
 *
 * @author Muntazir Fadhel
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({ @Type(value = AnnotationInvocation.class, name = "annotation"),
        @Type(value = EmptyInvocation.class, name = "empty"), @Type(value = ThrownException.class, name = "exception"),
        @Type(value = TypeDeclaration.class, name = "declaration"),
        @Type(value = TypeExtension.class, name = "extension"),
        @Type(value = TypeImplementation.class, name = "implementation"),
        @Type(value = TypeParameter.class, name = "typeparameter") })
public abstract class ComponentInvocation implements EmptyResource, Serializable, Cloneable {

    private static final long serialVersionUID = -242718695900611890L;
    private String invokedComponent = "";

    public ComponentInvocation(final String invocationComponentName, final int lineNum) {
        invokedComponent = invocationComponentName;
    }

    public ComponentInvocation(final ComponentInvocation invocation) {
        invokedComponent = invocation.invokedComponent();
    }

    public ComponentInvocation() {
    }

    public ComponentInvocation(String invokedComponent2) {
        invokedComponent = invokedComponent2;
    }

    public String invokedComponent() {
        return invokedComponent;
    }

    @Override
    public boolean empty() {
        return invokedComponent.isEmpty();
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }
}
