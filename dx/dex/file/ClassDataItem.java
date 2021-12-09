/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.pranav.ide.dx.dex.file;

import com.pranav.ide.dx.rop.cst.Constant;
import com.pranav.ide.dx.rop.cst.CstArray;
import com.pranav.ide.dx.rop.cst.CstLiteralBits;
import com.pranav.ide.dx.rop.cst.CstType;
import com.pranav.ide.dx.rop.cst.Zeroes;
import com.pranav.ide.dx.util.AnnotatedOutput;
import com.pranav.ide.dx.util.ByteArrayAnnotatedOutput;
import com.pranav.ide.dx.util.Writers;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

/**
 * Representation of all the parts of a Dalvik class that are generally
 * "inflated" into an in-memory representation at runtime. Instances of
 * this class are represented in a compact streamable form in a
 * {@code dex} file, as opposed to a random-access form.
 */
public final class ClassDataItem extends OffsettedItem {
    /** {@code non-null;} what class this data is for, just for listing generation */
    private final CstType thisClass;

    /** {@code non-null;} list of static fields */
    private final ArrayList<com.pranav.ide.dx.dex.file.EncodedField> staticFields;

    /** {@code non-null;} list of initial values for static fields */
    private final HashMap<com.pranav.ide.dx.dex.file.EncodedField, Constant> staticValues;

    /** {@code non-null;} list of instance fields */
    private final ArrayList<com.pranav.ide.dx.dex.file.EncodedField> instanceFields;

    /** {@code non-null;} list of direct methods */
    private final ArrayList<com.pranav.ide.dx.dex.file.EncodedMethod> directMethods;

    /** {@code non-null;} list of virtual methods */
    private final ArrayList<com.pranav.ide.dx.dex.file.EncodedMethod> virtualMethods;

    /** {@code null-ok;} static initializer list; set in {@link #addContents} */
    private CstArray staticValuesConstant;

    /**
     * {@code null-ok;} encoded form, ready for writing to a file; set during
     * {@link #place0}
     */
    private byte[] encodedForm;

    /**
     * Constructs an instance. Its sets of members are initially
     * empty.
     *
     * @param thisClass {@code non-null;} what class this data is for, just
     * for listing generation
     */
    public ClassDataItem(CstType thisClass) {
        super(1, -1);

        if (thisClass == null) {
            throw new NullPointerException("thisClass == null");
        }

        this.thisClass = thisClass;
        this.staticFields = new ArrayList<com.pranav.ide.dx.dex.file.EncodedField>(20);
        this.staticValues = new HashMap<com.pranav.ide.dx.dex.file.EncodedField, Constant>(40);
        this.instanceFields = new ArrayList<com.pranav.ide.dx.dex.file.EncodedField>(20);
        this.directMethods = new ArrayList<com.pranav.ide.dx.dex.file.EncodedMethod>(20);
        this.virtualMethods = new ArrayList<com.pranav.ide.dx.dex.file.EncodedMethod>(20);
        this.staticValuesConstant = null;
    }

    /** {@inheritDoc} */
    @Override
    public ItemType itemType() {
        return ItemType.TYPE_CLASS_DATA_ITEM;
    }

    /** {@inheritDoc} */
    @Override
    public String toHuman() {
        return toString();
    }

    /**
     * Returns whether this instance is empty.
     *
     * @return {@code true} if this instance is empty or
     * {@code false} if at least one element has been added to it
     */
    public boolean isEmpty() {
        return staticFields.isEmpty() && instanceFields.isEmpty()
            && directMethods.isEmpty() && virtualMethods.isEmpty();
    }

    /**
     * Adds a static field.
     *
     * @param field {@code non-null;} the field to add
     * @param value {@code null-ok;} initial value for the field, if any
     */
    public void addStaticField(com.pranav.ide.dx.dex.file.EncodedField field, Constant value) {
        if (field == null) {
            throw new NullPointerException("field == null");
        }

        if (staticValuesConstant != null) {
            throw new UnsupportedOperationException(
                    "static fields already sorted");
        }

        staticFields.add(field);
        staticValues.put(field, value);
    }

    /**
     * Adds an instance field.
     *
     * @param field {@code non-null;} the field to add
     */
    public void addInstanceField(com.pranav.ide.dx.dex.file.EncodedField field) {
        if (field == null) {
            throw new NullPointerException("field == null");
        }

        instanceFields.add(field);
    }

    /**
     * Adds a direct ({@code static} and/or {@code private}) method.
     *
     * @param method {@code non-null;} the method to add
     */
    public void addDirectMethod(com.pranav.ide.dx.dex.file.EncodedMethod method) {
        if (method == null) {
            throw new NullPointerException("method == null");
        }

        directMethods.add(method);
    }

    /**
     * Adds a virtual method.
     *
     * @param method {@code non-null;} the method to add
     */
    public void addVirtualMethod(com.pranav.ide.dx.dex.file.EncodedMethod method) {
        if (method == null) {
            throw new NullPointerException("method == null");
        }

        virtualMethods.add(method);
    }

    /**
     * Gets all the methods in this class. The returned list is not linked
     * in any way to the underlying lists contained in this instance, but
     * the objects contained in the list are shared.
     *
     * @return {@code non-null;} list of all methods
     */
    public ArrayList<com.pranav.ide.dx.dex.file.EncodedMethod> getMethods() {
        int sz = directMethods.size() + virtualMethods.size();
        ArrayList<com.pranav.ide.dx.dex.file.EncodedMethod> result = new ArrayList<com.pranav.ide.dx.dex.file.EncodedMethod>(sz);

        result.addAll(directMethods);
        result.addAll(virtualMethods);

        return result;
    }


    /**
     * Prints out the contents of this instance, in a debugging-friendly
     * way.
     *
     * @param out {@code non-null;} where to output to
     * @param verbose whether to be verbose with the output
     */
    public void debugPrint(Writer out, boolean verbose) {
        PrintWriter pw = Writers.printWriterFor(out);

        int sz = staticFields.size();
        for (int i = 0; i < sz; i++) {
            pw.println("  sfields[" + i + "]: " + staticFields.get(i));
        }

        sz = instanceFields.size();
        for (int i = 0; i < sz; i++) {
            pw.println("  ifields[" + i + "]: " + instanceFields.get(i));
        }

        sz = directMethods.size();
        for (int i = 0; i < sz; i++) {
            pw.println("  dmeths[" + i + "]:");
            directMethods.get(i).debugPrint(pw, verbose);
        }

        sz = virtualMethods.size();
        for (int i = 0; i < sz; i++) {
            pw.println("  vmeths[" + i + "]:");
            virtualMethods.get(i).debugPrint(pw, verbose);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void addContents(com.pranav.ide.dx.dex.file.DexFile file) {
        if (!staticFields.isEmpty()) {
            getStaticValuesConstant(); // Force the fields to be sorted.
            for (com.pranav.ide.dx.dex.file.EncodedField field : staticFields) {
                field.addContents(file);
            }
        }

        if (!instanceFields.isEmpty()) {
            Collections.sort(instanceFields);
            for (com.pranav.ide.dx.dex.file.EncodedField field : instanceFields) {
                field.addContents(file);
            }
        }

        if (!directMethods.isEmpty()) {
            Collections.sort(directMethods);
            for (com.pranav.ide.dx.dex.file.EncodedMethod method : directMethods) {
                method.addContents(file);
            }
        }

        if (!virtualMethods.isEmpty()) {
            Collections.sort(virtualMethods);
            for (EncodedMethod method : virtualMethods) {
                method.addContents(file);
            }
        }
    }

    /**
     * Gets a {@link CstArray} corresponding to {@link #staticValues} if
     * it contains any non-zero non-{@code null} values.
     *
     * @return {@code null-ok;} the corresponding constant or {@code null} if
     * there are no values to encode
     */
    public CstArray getStaticValuesConstant() {
        if ((staticValuesConstant == null) && (staticFields.size() != 0)) {
            staticValuesConstant = makeStaticValuesConstant();
        }

        return staticValuesConstant;
    }

    /**
     * Gets a {@link CstArray} corresponding to {@link #staticValues} if
     * it contains any non-zero non-{@code null} values.
     *
     * @return {@code null-ok;} the corresponding constant or {@code null} if
     * there are no values to encode
     */
    private CstArray makeStaticValuesConstant() {
        // First sort the statics into their final order.
        Collections.sort(staticFields);

        /*
         * Get the size of staticValues minus any trailing zeros/nulls (both
         * nulls per se as well as instances of CstKnownNull).
         */

        int size = staticFields.size();
        while (size > 0) {
            com.pranav.ide.dx.dex.file.EncodedField field = staticFields.get(size - 1);
            Constant cst = staticValues.get(field);
            if (cst instanceof CstLiteralBits) {
                // Note: CstKnownNull extends CstLiteralBits.
                if (((CstLiteralBits) cst).getLongBits() != 0) {
                    break;
                }
            } else if (cst != null) {
                break;
            }
            size--;
        }

        if (size == 0) {
            return null;
        }

        // There is something worth encoding, so build up a result.

        CstArray.List list = new CstArray.List(size);
        for (int i = 0; i < size; i++) {
            EncodedField field = staticFields.get(i);
            Constant cst = staticValues.get(field);
            if (cst == null) {
                cst = Zeroes.zeroFor(field.getRef().getType());
            }
            list.set(i, cst);
        }
        list.setImmutable();

        return new CstArray(list);
    }

    /** {@inheritDoc} */
    @Override
    protected void place0(Section addedTo, int offset) {
        // Encode the data and note the size.

        ByteArrayAnnotatedOutput out = new ByteArrayAnnotatedOutput();

        encodeOutput(addedTo.getFile(), out);
        encodedForm = out.toByteArray();
        setWriteSize(encodedForm.length);
    }

    /**
     * Writes out the encoded form of this instance.
     *
     * @param file {@code non-null;} file this instance is part of
     * @param out {@code non-null;} where to write to
     */
    private void encodeOutput(com.pranav.ide.dx.dex.file.DexFile file, AnnotatedOutput out) {
        boolean annotates = out.annotates();

        if (annotates) {
            out.annotate(0, offsetString() + " class data for " +
                    thisClass.toHuman());
        }

        encodeSize(file, out, "static_fields", staticFields.size());
        encodeSize(file, out, "instance_fields", instanceFields.size());
        encodeSize(file, out, "direct_methods", directMethods.size());
        encodeSize(file, out, "virtual_methods", virtualMethods.size());

        encodeList(file, out, "static_fields", staticFields);
        encodeList(file, out, "instance_fields", instanceFields);
        encodeList(file, out, "direct_methods", directMethods);
        encodeList(file, out, "virtual_methods", virtualMethods);

        if (annotates) {
            out.endAnnotation();
        }
    }

    /**
     * Helper for {@link #encodeOutput}, which writes out the given
     * size value, annotating it as well (if annotations are enabled).
     *
     * @param file {@code non-null;} file this instance is part of
     * @param out {@code non-null;} where to write to
     * @param label {@code non-null;} the label for the purposes of annotation
     * @param size {@code >= 0;} the size to write
     */
    private static void encodeSize(com.pranav.ide.dx.dex.file.DexFile file, AnnotatedOutput out,
                                   String label, int size) {
        if (out.annotates()) {
            out.annotate(String.format("  %-21s %08x", label + "_size:",
                            size));
        }

        out.writeUleb128(size);
    }

    /**
     * Helper for {@link #encodeOutput}, which writes out the given
     * list. It also annotates the items (if any and if annotations
     * are enabled).
     *
     * @param file {@code non-null;} file this instance is part of
     * @param out {@code non-null;} where to write to
     * @param label {@code non-null;} the label for the purposes of annotation
     * @param list {@code non-null;} the list in question
     */
    private static void encodeList(com.pranav.ide.dx.dex.file.DexFile file, AnnotatedOutput out,
                                   String label, ArrayList<? extends EncodedMember> list) {
        int size = list.size();
        int lastIndex = 0;

        if (size == 0) {
            return;
        }

        if (out.annotates()) {
            out.annotate(0, "  " + label + ":");
        }

        for (int i = 0; i < size; i++) {
            lastIndex = list.get(i).encode(file, out, lastIndex, i);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void writeTo0(DexFile file, AnnotatedOutput out) {
        boolean annotates = out.annotates();

        if (annotates) {
            /*
             * The output is to be annotated, so redo the work previously
             * done by place0(), except this time annotations will actually
             * get emitted.
             */
            encodeOutput(file, out);
        } else {
            out.write(encodedForm);
        }
    }
}