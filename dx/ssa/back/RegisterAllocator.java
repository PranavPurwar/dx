/*
 * Copyright (C) 2007 The Android Open Source Project
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

package com.pranav.ide.dx.ssa.back;

import com.pranav.ide.dx.ssa.NormalSsaInsn;
import com.pranav.ide.dx.ssa.RegisterMapper;
import com.pranav.ide.dx.ssa.SsaBasicBlock;
import com.pranav.ide.dx.ssa.SsaInsn;
import com.pranav.ide.dx.ssa.SsaMethod;
import com.pranav.ide.dx.util.IntIterator;
import com.pranav.ide.dx.util.IntSet;
import java.util.ArrayList;

import com.pranav.ide.dx.rop.code.PlainInsn;
import com.pranav.ide.dx.rop.code.RegOps;
import com.pranav.ide.dx.rop.code.RegisterSpec;
import com.pranav.ide.dx.rop.code.RegisterSpecList;
import com.pranav.ide.dx.rop.code.Rops;
import com.pranav.ide.dx.rop.code.SourcePosition;

/**
 * Base class of all register allocators.
 */
public abstract class RegisterAllocator {
    /** method being processed */
    protected final SsaMethod ssaMeth;

    /** interference graph, indexed by register in both dimensions */
    protected final com.pranav.ide.dx.ssa.back.InterferenceGraph interference;

    /**
     * Creates an instance. Call {@code allocateRegisters} to run.
     * @param ssaMeth method to process.
     * @param interference Interference graph, indexed by register in both
     * dimensions.
     */
    public RegisterAllocator(SsaMethod ssaMeth,
            InterferenceGraph interference) {
        this.ssaMeth = ssaMeth;
        this.interference = interference;
    }

    /**
     * Indicates whether the method params were allocated at the bottom
     * of the namespace, and thus should be moved up to the top of the
     * namespace after phi removal.
     *
     * @return {@code true} if params should be moved from low to high
     */
    public abstract boolean wantsParamsMovedHigh();

    /**
     * Runs the algorithm.
     *
     * @return a register mapper to apply to the {@code SsaMethod}
     */
    public abstract RegisterMapper allocateRegisters();

    /**
     * Returns the category (width) of the definition site of the register.
     * Returns {@code 1} for undefined registers.
     *
     * @param reg register
     * @return {@code 1..2}
     */
    protected final int getCategoryForSsaReg(int reg) {
        SsaInsn definition = ssaMeth.getDefinitionForRegister(reg);

        if (definition == null) {
            // an undefined reg
            return 1;
        } else {
            return definition.getResult().getCategory();
        }
    }

    /**
     * Returns the RegisterSpec of the definition of the register.
     *
     * @param reg {@code >= 0;} SSA register
     * @return definition spec of the register or null if it is never defined
     * (for the case of "version 0" SSA registers)
     */
    protected final com.pranav.ide.dx.rop.code.RegisterSpec getDefinitionSpecForSsaReg(int reg) {
        SsaInsn definition = ssaMeth.getDefinitionForRegister(reg);

        return definition == null ? null : definition.getResult();
    }

    /**
     * Returns true if the definition site of this register is a
     * move-param (ie, this is a method parameter).
     *
     * @param reg register in question
     * @return {@code true} if this is a method parameter
     */
    protected boolean isDefinitionMoveParam(int reg) {
        SsaInsn defInsn = ssaMeth.getDefinitionForRegister(reg);

        if (defInsn instanceof NormalSsaInsn) {
            NormalSsaInsn ndefInsn = (NormalSsaInsn) defInsn;

            return ndefInsn.getOpcode().getOpcode() == RegOps.MOVE_PARAM;
        }

        return false;
    }

    /**
     * Inserts a move instruction for a specified SSA register before a
     * specified instruction, creating a new SSA register and adjusting the
     * interference graph in the process. The insn currently must be the
     * last insn in a block.
     *
     * @param insn {@code non-null;} insn to insert move before, must
     * be last insn in block
     * @param reg {@code non-null;} SSA register to duplicate
     * @return {@code non-null;} spec of new SSA register created by move
     */
    protected final com.pranav.ide.dx.rop.code.RegisterSpec insertMoveBefore(SsaInsn insn,
                                                                               com.pranav.ide.dx.rop.code.RegisterSpec reg) {
        SsaBasicBlock block = insn.getBlock();
        ArrayList<SsaInsn> insns = block.getInsns();
        int insnIndex = insns.indexOf(insn);

        if (insnIndex < 0) {
            throw new IllegalArgumentException (
                    "specified insn is not in this block");
        }

        if (insnIndex != insns.size() - 1) {
            /*
             * Presently, the interference updater only works when
             * adding before the last insn, and the last insn must have no
             * result
             */
            throw new IllegalArgumentException(
                    "Adding move here not supported:" + insn.toHuman());
        }

        /*
         * Get new register and make new move instruction.
         */

        // The new result must not have an associated local variable.
        com.pranav.ide.dx.rop.code.RegisterSpec newRegSpec = RegisterSpec.make(ssaMeth.makeNewSsaReg(),
                reg.getTypeBearer());

        SsaInsn toAdd = SsaInsn.makeFromRop(
                new PlainInsn(Rops.opMove(newRegSpec.getType()),
                        SourcePosition.NO_INFO, newRegSpec,
                        com.pranav.ide.dx.rop.code.RegisterSpecList.make(reg)), block);

        insns.add(insnIndex, toAdd);

        int newReg = newRegSpec.getReg();

        /*
         * Adjust interference graph based on what's live out of the current
         * block and what's used by the final instruction.
         */

        IntSet liveOut = block.getLiveOutRegs();
        IntIterator liveOutIter = liveOut.iterator();

        while (liveOutIter.hasNext()) {
            interference.add(newReg, liveOutIter.next());
        }

        // Everything that's a source in the last insn interferes.
        RegisterSpecList sources = insn.getSources();
        int szSources = sources.size();

        for (int i = 0; i < szSources; i++) {
            interference.add(newReg, sources.get(i).getReg());
        }

        ssaMeth.onInsnsChanged();

        return newRegSpec;
    }
}
