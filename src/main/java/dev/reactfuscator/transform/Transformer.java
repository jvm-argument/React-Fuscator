package dev.reactfuscator.transform;

import dev.reactfuscator.core.ObfuscationContext;
import dev.reactfuscator.model.ClassModel;

public interface Transformer {
    TransformerDescriptor descriptor();

    void transform(ObfuscationContext context, ClassModel model);
}
