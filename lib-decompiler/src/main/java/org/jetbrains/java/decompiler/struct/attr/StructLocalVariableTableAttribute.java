// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.decompiler.struct.attr;

import com.duy.java8.util.DList;
import com.duy.java8.util.DMap;
import com.duy.java8.util.function.BinaryOperator;
import com.duy.java8.util.function.Function;
import com.duy.java8.util.function.Predicate;

import org.jetbrains.java.decompiler.struct.consts.ConstantPool;
import org.jetbrains.java.decompiler.util.DataInputFullStream;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
  u2 local_variable_table_length;
  local_variable {
    u2 start_pc;
    u2 length;
    u2 name_index;
    u2 descriptor_index;
    u2 index;
  }
*/
public class StructLocalVariableTableAttribute extends StructGeneralAttribute {
    private List<LocalVariable> localVariables = Collections.emptyList();

    @Override
    public void initContent(DataInputFullStream data, ConstantPool pool) throws IOException {
        int len = data.readUnsignedShort();
        if (len > 0) {
            localVariables = new ArrayList<>(len);

            for (int i = 0; i < len; i++) {
                int start_pc = data.readUnsignedShort();
                int length = data.readUnsignedShort();
                int nameIndex = data.readUnsignedShort();
                int descriptorIndex = data.readUnsignedShort();
                int varIndex = data.readUnsignedShort();
                localVariables.add(new LocalVariable(start_pc,
                        length,
                        pool.getPrimitiveConstant(nameIndex).getString(),
                        pool.getPrimitiveConstant(descriptorIndex).getString(),
                        varIndex));
            }
        } else {
            localVariables = Collections.emptyList();
        }
    }

    public void add(StructLocalVariableTableAttribute attr) {
        localVariables.addAll(attr.localVariables);
    }

    public String getName(int index, int visibleOffset) {
        List<LocalVariable> localVariables = matchingVars(index, visibleOffset);
        return localVariables.size() >= 1 ? localVariables.get(0).name : null;
    }

    public String getDescriptor(int index, int visibleOffset) {
        List<LocalVariable> localVariables = matchingVars(index, visibleOffset);
        return localVariables.size() >= 1 ? localVariables.get(0).descriptor : null;
    }

    private List<LocalVariable> matchingVars(final int index, final int visibleOffset) {
        return DList.filter(localVariables,
                new Predicate<LocalVariable>() {
                    @Override
                    public boolean test(LocalVariable v) {
                        return v.index == index && (visibleOffset >= v.start_pc && visibleOffset < v.start_pc + v.length);
                    }
                });
    }

    public boolean containsName(final String name) {

        Predicate<LocalVariable> predicate = new Predicate<LocalVariable>() {
            @Override
            public boolean test(LocalVariable v) {
                return v.name == name;
            }
        };
        for (LocalVariable localVariable : localVariables) {
            if (predicate.test(localVariable)) {
                return true;
            }
        }
        return false;
    }

    public Map<Integer, String> getMapParamNames() {
        Predicate<LocalVariable> predicate = new Predicate<LocalVariable>() {
            @Override
            public boolean test(LocalVariable v) {
                return v.start_pc == 0;
            }
        };
        List<LocalVariable> filterd = DList.filter(localVariables, predicate);
        for (LocalVariable localVariable : filterd) {

        }
        Function<LocalVariable, Integer> keyMapper = new Function<LocalVariable, Integer>() {
            @Override
            public Integer apply(LocalVariable v) {
                return v.index;
            }
        };
        Function<LocalVariable, String> valueMapper = new Function<LocalVariable, String>() {
            @Override
            public String apply(LocalVariable v) {
                return v.name;
            }
        };
        BinaryOperator<String> mergeFunction = new BinaryOperator<String>() {
            @Override
            public String apply(String n1, String n2) {
                return n2;
            }
        };
        HashMap<Integer, String> hashMap = new HashMap<>();

        for (LocalVariable element : filterd) {
            DMap.merge(hashMap, keyMapper.apply(element),
                    valueMapper.apply(element),
                    mergeFunction);
        }
        return hashMap;
    }

    private static class LocalVariable {
        final int start_pc;
        final int length;
        final String name;
        final String descriptor;
        final int index;

        private LocalVariable(int start_pc, int length, String name, String descriptor, int index) {
            this.start_pc = start_pc;
            this.length = length;
            this.name = name;
            this.descriptor = descriptor;
            this.index = index;
        }
    }
}
