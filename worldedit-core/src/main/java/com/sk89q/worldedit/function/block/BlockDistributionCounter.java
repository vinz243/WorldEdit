/*
 * WorldEdit, a Minecraft world manipulation toolkit
 * Copyright (C) sk89q <http://www.sk89q.com>
 * Copyright (C) WorldEdit team and contributors
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.sk89q.worldedit.function.block;

import com.sk89q.worldedit.Vector;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.function.RegionFunction;
import com.sk89q.worldedit.util.Countable;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockStateHolder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BlockDistributionCounter implements RegionFunction {

    private Extent extent;
    private boolean fuzzy;

    private List<Countable<BlockStateHolder>> distribution = new ArrayList<>();
    private Map<BlockStateHolder, Countable<BlockStateHolder>> map = new HashMap<>();

    public BlockDistributionCounter(Extent extent, boolean fuzzy) {
        this.extent = extent;
        this.fuzzy = fuzzy;
    }

    @Override
    public boolean apply(Vector position) throws WorldEditException {
        BlockStateHolder blk = extent.getBlock(position);
        if (fuzzy) {
            blk = ((BlockState) blk).toFuzzy();
        }

        if (map.containsKey(blk)) {
            map.get(blk).increment();
        } else {
            Countable<BlockStateHolder> c = new Countable<>(blk, 1);
            map.put(blk, c);
            distribution.add(c);
        }

        return true;
    }

    /**
     * Gets the distribution list.
     *
     * @return The distribution
     */
    public List<Countable<BlockStateHolder>> getDistribution() {
        Collections.sort(distribution);
        Collections.reverse(distribution);
        return this.distribution;
    }
}
