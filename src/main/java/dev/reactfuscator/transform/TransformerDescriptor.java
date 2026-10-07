package dev.reactfuscator.transform;

import dev.reactfuscator.config.ProtectionProfile;

public record TransformerDescriptor(String id,String name,String description,int order,ProtectionProfile minimumProfile,boolean densityConfigurable,boolean roundsConfigurable) {
    public TransformerDescriptor(String id,String name,String description,int order,ProtectionProfile minimumProfile) { this(id,name,description,order,minimumProfile,true,false); }
    public boolean defaultEnabled(ProtectionProfile profile) { return profile.ordinal()>=minimumProfile.ordinal(); }
}
