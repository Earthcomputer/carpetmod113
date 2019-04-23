package carpet;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.block.state.IBlockState;
import net.minecraft.command.arguments.BlockStateParser;
import net.minecraft.init.Bootstrap;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.util.datafix.fixes.BlockStateFlatteningMap;

import java.io.*;

public class SchemDowngrader {

    public static void main(String[] args) throws IOException, CommandSyntaxException {
        PrintStream sysout = System.out;
        Bootstrap.register();
        System.setOut(sysout);

        NBTTagCompound schem13 = CompressedStreamTools.readCompressed(new FileInputStream(new File("/home/joe/Downloads/64mult.schem")));

        int[] blockMap = new int[schem13.getInt("PaletteMax") + 1];

        for (String blockKey : schem13.getCompound("Palette").keySet()) {
            IBlockState fixedState = new BlockStateParser(new StringReader(blockKey), false).parse(false).getState();
            NBTTagCompound fixedNBT = NBTUtil.writeBlockState(fixedState);

            String blockName = fixedNBT.getString("Name");
            NBTTagCompound properties = fixedNBT.getCompound("Properties");
            int blockStateId;
            if ("minecraft:redstone_wire".equals(blockName)) {
                blockStateId = 880 + properties.getInt("power");
            } else {
                if ("minecraft:repeater".equals(blockName)) {
                    properties.removeTag("locked");
                }
                blockStateId = BlockStateFlatteningMap.FIXED_NBT_TO_ID.getInt(fixedNBT);
                if (blockStateId == -1) {
                    for (NBTTagCompound nbt : BlockStateFlatteningMap.FIXED_NBT_TO_ID.keySet()) {
                        if (NBTUtil.areNBTEquals(fixedNBT, nbt, true) || NBTUtil.areNBTEquals(nbt, fixedNBT, true)) {
                            blockStateId = BlockStateFlatteningMap.FIXED_NBT_TO_ID.getInt(nbt);
                            break;
                        }
                    }
                }
                if ("minecraft:comparator".equals(blockName) && blockStateId >= 2400)
                    blockStateId -= 16;
            }

            if (blockStateId == -1) {
                throw new IOException("Cannot downgrade blockstate: " + blockKey);
            }

            blockMap[schem13.getCompound("Palette").getInt(blockKey)] = blockStateId;
        }

        byte[] blocks = schem13.getByteArray("BlockData");
        schem13.removeTag("BlockData");
        int index = 0;
        int i = 0;
        int value = 0;
        int varintLength = 0;

        byte[] blockIds = new byte[schem13.getInt("Width") * schem13.getInt("Height") * schem13.getInt("Length")];
        byte[] blockMeta = new byte[blockIds.length];
        while (i < blocks.length) {
            value = 0;
            varintLength = 0;

            while (true) {
                value |= (blocks[i] & 127) << (varintLength++ * 7);
                if (varintLength > 5) {
                    throw new RuntimeException("VarInt too big (probably corrupted data)");
                }
                if ((blocks[i] & 128) != 128) {
                    i++;
                    break;
                }
                i++;
            }

            int blockstateId = blockMap[value];
            blockIds[index] = (byte) (blockstateId >>> 4);
            blockMeta[index] = (byte) (blockstateId & 15);

            index++;
        }

        schem13.setByteArray("Blocks", blockIds);
        schem13.setByteArray("Data", blockMeta);

        NBTTagList tileEntities = schem13.getList("TileEntities", 10);
        for (i = 0; i < tileEntities.size(); i++) {
            NBTTagCompound te = tileEntities.getCompound(i);
            int[] pos = te.getIntArray("Pos");
            te.setInt("x", pos[0]);
            te.setInt("y", pos[1]);
            te.setInt("z", pos[2]);
            te.setString("id", te.getString("Id"));
            te.removeTag("Pos");
            te.removeTag("Id");
        }

        schem13.setString("Materials", "Alpha");

        CompressedStreamTools.writeCompressed(schem13, new FileOutputStream(new File("/home/joe/Downloads/64mult.schematic")));
    }

}
