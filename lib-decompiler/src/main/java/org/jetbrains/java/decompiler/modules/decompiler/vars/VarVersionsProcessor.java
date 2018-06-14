// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.decompiler.modules.decompiler.vars;

import org.jetbrains.java.decompiler.code.CodeConstants;
import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.main.collectors.CounterContainer;
import org.jetbrains.java.decompiler.modules.decompiler.exps.ConstExprent;
import org.jetbrains.java.decompiler.modules.decompiler.exps.Exprent;
import org.jetbrains.java.decompiler.modules.decompiler.exps.VarExprent;
import org.jetbrains.java.decompiler.modules.decompiler.sforms.DirectGraph;
import org.jetbrains.java.decompiler.modules.decompiler.sforms.FlattenStatementsHelper;
import org.jetbrains.java.decompiler.modules.decompiler.sforms.SSAConstructorSparseEx;
import org.jetbrains.java.decompiler.modules.decompiler.stats.RootStatement;
import org.jetbrains.java.decompiler.struct.StructMethod;
import org.jetbrains.java.decompiler.struct.gen.MethodDescriptor;
import org.jetbrains.java.decompiler.struct.gen.VarType;
import org.jetbrains.java.decompiler.util.FastSparseSetFactory.FastSparseSet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Function;

public class VarVersionsProcessor {
    private final StructMethod method;
    private final VarTypeProcessor typeProcessor;
    private Map<Integer, Integer> mapOriginalVarIndices = Collections.emptyMap();

    public VarVersionsProcessor(StructMethod mt, MethodDescriptor md) {
        method = mt;
        typeProcessor = new VarTypeProcessor(mt, md);
    }

    private static void mergePhiVersions(SSAConstructorSparseEx ssa, DirectGraph graph) {
        // collect phi versions
        List<Set<VarVersionPair>> lst = new ArrayList<>();
        for (Entry<VarVersionPair, FastSparseSet<Integer>> ent : ssa.getPhi().entrySet()) {
            Set<VarVersionPair> set = new HashSet<>();
            set.add(ent.getKey());
            for (Integer version : ent.getValue()) {
                set.add(new VarVersionPair(ent.getKey().var, version.intValue()));
            }

            for (int i = lst.size() - 1; i >= 0; i--) {
                Set<VarVersionPair> tset = lst.get(i);
                Set<VarVersionPair> intersection = new HashSet<>(set);
                intersection.retainAll(tset);

                if (!intersection.isEmpty()) {
                    set.addAll(tset);
                    lst.remove(i);
                }
            }

            lst.add(set);
        }

        Map<VarVersionPair, Integer> phiVersions = new HashMap<>();
        for (Set<VarVersionPair> set : lst) {
            int min = Integer.MAX_VALUE;
            for (VarVersionPair paar : set) {
                if (paar.version < min) {
                    min = paar.version;
                }
            }

            for (VarVersionPair paar : set) {
                phiVersions.put(new VarVersionPair(paar.var, paar.version), min);
            }
        }

        updateVersions(graph, phiVersions);
    }

    private static void updateVersions(DirectGraph graph, final Map<VarVersionPair, Integer> versions) {
        graph.iterateExprents(new DirectGraph.ExprentIterator() {
            @Override
            public int processExprent(Exprent exprent) {
                List<Exprent> lst = exprent.getAllExprents(true);
                lst.add(exprent);

                for (Exprent expr : lst) {
                    if (expr.type == Exprent.EXPRENT_VAR) {
                        VarExprent var = (VarExprent) expr;
                        Integer version = versions.get(new VarVersionPair(var));
                        if (version != null) {
                            var.setVersion(version);
                        }
                    }
                }

                return 0;
            }
        });
    }

    private static void eliminateNonJavaTypes(VarTypeProcessor typeProcessor) {
        Map<VarVersionPair, VarType> mapExprentMaxTypes = typeProcessor.getMapExprentMaxTypes();
        Map<VarVersionPair, VarType> mapExprentMinTypes = typeProcessor.getMapExprentMinTypes();

        for (VarVersionPair paar : new ArrayList<>(mapExprentMinTypes.keySet())) {
            VarType type = mapExprentMinTypes.get(paar);
            VarType maxType = mapExprentMaxTypes.get(paar);

            if (type.type == CodeConstants.TYPE_BYTECHAR || type.type == CodeConstants.TYPE_SHORTCHAR) {
                if (maxType != null && maxType.type == CodeConstants.TYPE_CHAR) {
                    type = VarType.VARTYPE_CHAR;
                } else {
                    type = type.type == CodeConstants.TYPE_BYTECHAR ? VarType.VARTYPE_BYTE : VarType.VARTYPE_SHORT;
                }
                mapExprentMinTypes.put(paar, type);
                //} else if(type.type == CodeConstants.TYPE_CHAR && (maxType == null || maxType.type == CodeConstants.TYPE_INT)) { // when possible, lift char to int
                //	mapExprentMinTypes.put(paar, VarType.VARTYPE_INT);
            } else if (type.type == CodeConstants.TYPE_NULL) {
                mapExprentMinTypes.put(paar, VarType.VARTYPE_OBJECT);
            }
        }
    }

    private static void simpleMerge(VarTypeProcessor typeProcessor, DirectGraph graph, StructMethod mt) {
        Map<VarVersionPair, VarType> mapExprentMaxTypes = typeProcessor.getMapExprentMaxTypes();
        Map<VarVersionPair, VarType> mapExprentMinTypes = typeProcessor.getMapExprentMinTypes();

        Map<Integer, Set<Integer>> mapVarVersions = new HashMap<>();

        for (VarVersionPair pair : mapExprentMinTypes.keySet()) {
            if (pair.version >= 0) {  // don't merge constants
                mapVarVersions.computeIfAbsent(pair.var, new Function<Integer, Set<Integer>>() {
                    @Override
                    public Set<Integer> apply(Integer k) {
                        return new HashSet<>();
                    }
                }).add(pair.version);
            }
        }

        boolean is_method_static = mt.hasModifier(CodeConstants.ACC_STATIC);

        Map<VarVersionPair, Integer> mapMergedVersions = new HashMap<>();

        for (Entry<Integer, Set<Integer>> ent : mapVarVersions.entrySet()) {

            if (ent.getValue().size() > 1) {
                List<Integer> lstVersions = new ArrayList<>(ent.getValue());
                Collections.sort(lstVersions);

                for (int i = 0; i < lstVersions.size(); i++) {
                    VarVersionPair firstPair = new VarVersionPair(ent.getKey(), lstVersions.get(i));
                    VarType firstType = mapExprentMinTypes.get(firstPair);

                    if (firstPair.var == 0 && firstPair.version == 1 && !is_method_static) {
                        continue; // don't merge 'this' variable
                    }

                    for (int j = i + 1; j < lstVersions.size(); j++) {
                        VarVersionPair secondPair = new VarVersionPair(ent.getKey(), lstVersions.get(j));
                        VarType secondType = mapExprentMinTypes.get(secondPair);

                        if (firstType.equals(secondType) ||
                                (firstType.equals(VarType.VARTYPE_NULL) && secondType.type == CodeConstants.TYPE_OBJECT) ||
                                (secondType.equals(VarType.VARTYPE_NULL) && firstType.type == CodeConstants.TYPE_OBJECT)) {

                            VarType firstMaxType = mapExprentMaxTypes.get(firstPair);
                            VarType secondMaxType = mapExprentMaxTypes.get(secondPair);
                            VarType type = firstMaxType == null ? secondMaxType :
                                    secondMaxType == null ? firstMaxType :
                                            VarType.getCommonMinType(firstMaxType, secondMaxType);

                            mapExprentMaxTypes.put(firstPair, type);
                            mapMergedVersions.put(secondPair, firstPair.version);
                            mapExprentMaxTypes.remove(secondPair);
                            mapExprentMinTypes.remove(secondPair);

                            if (firstType.equals(VarType.VARTYPE_NULL)) {
                                mapExprentMinTypes.put(firstPair, secondType);
                                firstType = secondType;
                            }

                            typeProcessor.getMapFinalVars().put(firstPair, VarTypeProcessor.VAR_NON_FINAL);

                            lstVersions.remove(j);
                            //noinspection AssignmentToForLoopParameter
                            j--;
                        }
                    }
                }
            }
        }

        if (!mapMergedVersions.isEmpty()) {
            updateVersions(graph, mapMergedVersions);
        }
    }

    public void setVarVersions(RootStatement root, VarVersionsProcessor previousVersionsProcessor) {
        SSAConstructorSparseEx ssa = new SSAConstructorSparseEx();
        ssa.splitVariables(root, method);

        FlattenStatementsHelper flattenHelper = new FlattenStatementsHelper();
        DirectGraph graph = flattenHelper.buildDirectGraph(root);

        mergePhiVersions(ssa, graph);

        typeProcessor.calculateVarTypes(root, graph);

        simpleMerge(typeProcessor, graph, method);

        // FIXME: advanced merging

        eliminateNonJavaTypes(typeProcessor);

        setNewVarIndices(typeProcessor, graph, previousVersionsProcessor);
    }

    private void setNewVarIndices(VarTypeProcessor typeProcessor, DirectGraph graph, VarVersionsProcessor previousVersionsProcessor) {
        final Map<VarVersionPair, VarType> mapExprentMaxTypes = typeProcessor.getMapExprentMaxTypes();
        Map<VarVersionPair, VarType> mapExprentMinTypes = typeProcessor.getMapExprentMinTypes();
        Map<VarVersionPair, Integer> mapFinalVars = typeProcessor.getMapFinalVars();

        CounterContainer counters = DecompilerContext.getCounterContainer();

        final Map<VarVersionPair, Integer> mapVarPaar = new HashMap<>();
        Map<Integer, Integer> mapOriginalVarIndices = new HashMap<>();

        // map var-version pairs on new var indexes
        for (VarVersionPair pair : new ArrayList<>(mapExprentMinTypes.keySet())) {

            if (pair.version >= 0) {
                int newIndex = pair.version == 1 ? pair.var : counters.getCounterAndIncrement(CounterContainer.VAR_COUNTER);

                VarVersionPair newVar = new VarVersionPair(newIndex, 0);

                mapExprentMinTypes.put(newVar, mapExprentMinTypes.get(pair));
                mapExprentMaxTypes.put(newVar, mapExprentMaxTypes.get(pair));

                if (mapFinalVars.containsKey(pair)) {
                    mapFinalVars.put(newVar, mapFinalVars.remove(pair));
                }

                mapVarPaar.put(pair, newIndex);
                mapOriginalVarIndices.put(newIndex, pair.var);
            }
        }

        // set new vars
        graph.iterateExprents(new DirectGraph.ExprentIterator() {
            @Override
            public int processExprent(Exprent exprent) {
                List<Exprent> lst = exprent.getAllExprents(true);
                lst.add(exprent);

                for (Exprent expr : lst) {
                    if (expr.type == Exprent.EXPRENT_VAR) {
                        VarExprent newVar = (VarExprent) expr;
                        Integer newVarIndex = mapVarPaar.get(new VarVersionPair(newVar));
                        if (newVarIndex != null) {
                            newVar.setIndex(newVarIndex);
                            newVar.setVersion(0);
                        }
                    } else if (expr.type == Exprent.EXPRENT_CONST) {
                        VarType maxType = mapExprentMaxTypes.get(new VarVersionPair(expr.id, -1));
                        if (maxType != null && maxType.equals(VarType.VARTYPE_CHAR)) {
                            ((ConstExprent) expr).setConstType(maxType);
                        }
                    }
                }

                return 0;
            }
        });

        if (previousVersionsProcessor != null) {
            Map<Integer, Integer> oldIndices = previousVersionsProcessor.getMapOriginalVarIndices();
            this.mapOriginalVarIndices = new HashMap<>(mapOriginalVarIndices.size());
            for (Entry<Integer, Integer> entry : mapOriginalVarIndices.entrySet()) {
                Integer value = entry.getValue();
                Integer oldValue = oldIndices.get(value);
                value = oldValue != null ? oldValue : value;
                this.mapOriginalVarIndices.put(entry.getKey(), value);
            }
        } else {
            this.mapOriginalVarIndices = mapOriginalVarIndices;
        }
    }

    public VarType getVarType(VarVersionPair pair) {
        return typeProcessor.getVarType(pair);
    }

    public void setVarType(VarVersionPair pair, VarType type) {
        typeProcessor.setVarType(pair, type);
    }

    public int getVarFinal(VarVersionPair pair) {
        Integer fin = typeProcessor.getMapFinalVars().get(pair);
        return fin == null ? VarTypeProcessor.VAR_FINAL : fin;
    }

    public void setVarFinal(VarVersionPair pair, int finalType) {
        typeProcessor.getMapFinalVars().put(pair, finalType);
    }

    public Map<Integer, Integer> getMapOriginalVarIndices() {
        return mapOriginalVarIndices;
    }
}