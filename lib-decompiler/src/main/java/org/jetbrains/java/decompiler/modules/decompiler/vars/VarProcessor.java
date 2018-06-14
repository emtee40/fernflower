// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.decompiler.modules.decompiler.vars;

import com.duy.java8.util.function.ToIntFunction;

import org.jetbrains.java.decompiler.main.collectors.VarNamesCollector;
import org.jetbrains.java.decompiler.modules.decompiler.stats.RootStatement;
import org.jetbrains.java.decompiler.modules.decompiler.stats.Statement;
import org.jetbrains.java.decompiler.struct.StructMethod;
import org.jetbrains.java.decompiler.struct.gen.MethodDescriptor;
import org.jetbrains.java.decompiler.struct.gen.VarType;
import org.jetbrains.java.decompiler.util.TextUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

public class VarProcessor {
    private final VarNamesCollector varNamesCollector = new VarNamesCollector();
    private final StructMethod method;
    private final MethodDescriptor methodDescriptor;
    private final Map<VarVersionPair, String> thisVars = new HashMap<>();
    private final Set<VarVersionPair> externalVars = new HashSet<>();
    private Map<VarVersionPair, String> mapVarNames = new HashMap<>();
    private VarVersionsProcessor varVersions;

    public VarProcessor(StructMethod mt, MethodDescriptor md) {
        method = mt;
        methodDescriptor = md;
    }

    public void setVarVersions(RootStatement root) {
        VarVersionsProcessor oldProcessor = varVersions;
        varVersions = new VarVersionsProcessor(method, methodDescriptor);
        varVersions.setVarVersions(root, oldProcessor);
    }

    public void setVarDefinitions(Statement root) {
        mapVarNames = new HashMap<>();
        new VarDefinitionHelper(root, method, this).setVarDefinitions();
    }

    public void setDebugVarNames(Map<Integer, String> mapDebugVarNames) {
        if (varVersions == null) {
            return;
        }

        Map<Integer, Integer> mapOriginalVarIndices = varVersions.getMapOriginalVarIndices();

        List<VarVersionPair> listVars = new ArrayList<>(mapVarNames.keySet());
        final ToIntFunction<VarVersionPair> keyExtractor = new ToIntFunction<VarVersionPair>() {
            @Override
            public int applyAsInt(VarVersionPair o) {
                return o.var;
            }
        };
        Comparator<VarVersionPair> comparator = new Comparator<VarVersionPair>() {
            @Override
            public int compare(VarVersionPair c1, VarVersionPair c2) {
                return Integer.compare(keyExtractor.applyAsInt(c1), keyExtractor.applyAsInt(c2));
            }
        };
        listVars.sort(comparator);

        Map<String, Integer> mapNames = new HashMap<>();

        for (VarVersionPair pair : listVars) {
            String name = mapVarNames.get(pair);

            Integer index = mapOriginalVarIndices.get(pair.var);
            if (index != null) {
                String debugName = mapDebugVarNames.get(index);
                if (debugName != null && TextUtil.isValidIdentifier(debugName, method.getClassStruct().getBytecodeVersion())) {
                    name = debugName;
                }
            }

            Integer counter = mapNames.get(name);
            mapNames.put(name, counter == null ? counter = 0 : ++counter);

            if (counter > 0) {
                name += String.valueOf(counter);
            }

            mapVarNames.put(pair, name);
        }
    }

    public Integer getVarOriginalIndex(int index) {
        if (varVersions == null) {
            return null;
        }

        return varVersions.getMapOriginalVarIndices().get(index);
    }

    public void refreshVarNames(VarNamesCollector vc) {
        Map<VarVersionPair, String> tempVarNames = new HashMap<>(mapVarNames);
        for (Entry<VarVersionPair, String> ent : tempVarNames.entrySet()) {
            mapVarNames.put(ent.getKey(), vc.getFreeName(ent.getValue()));
        }
    }

    public VarNamesCollector getVarNamesCollector() {
        return varNamesCollector;
    }

    public VarType getVarType(VarVersionPair pair) {
        return varVersions == null ? null : varVersions.getVarType(pair);
    }

    public void setVarType(VarVersionPair pair, VarType type) {
        varVersions.setVarType(pair, type);
    }

    public String getVarName(VarVersionPair pair) {
        return mapVarNames == null ? null : mapVarNames.get(pair);
    }

    public void setVarName(VarVersionPair pair, String name) {
        mapVarNames.put(pair, name);
    }

    public Collection<String> getVarNames() {
        return mapVarNames != null ? mapVarNames.values() : Collections.<String>emptySet();
    }

    public int getVarFinal(VarVersionPair pair) {
        return varVersions == null ? VarTypeProcessor.VAR_FINAL : varVersions.getVarFinal(pair);
    }

    public void setVarFinal(VarVersionPair pair, int finalType) {
        varVersions.setVarFinal(pair, finalType);
    }

    public Map<VarVersionPair, String> getThisVars() {
        return thisVars;
    }

    public Set<VarVersionPair> getExternalVars() {
        return externalVars;
    }
}