import net.minecraft.nbt.*;
import java.nio.file.*;

/** Rebuilds the small, self-contained Ponder stage used by both tutorials. */
class GenerateReinforcedBeltPonder {
    static ListTag ints(int... values) {
        var list=new ListTag();for(int value:values)list.add(IntTag.valueOf(value));return list;
    }
    static CompoundTag state(String name,String axis) {
        var state=new CompoundTag();state.putString("Name",name);
        if(axis!=null){var properties=new CompoundTag();properties.putString("axis",axis);state.put("Properties",properties);}
        return state;
    }
    static CompoundTag block(int x,int y,int z,int state) {
        var block=new CompoundTag();block.put("pos",ints(x,y,z));block.putInt("state",state);return block;
    }
    public static void main(String[] args) throws Exception {
        var root=new CompoundTag();root.putInt("DataVersion",3955);root.put("size",ints(9,6,9));
        var palette=new ListTag();palette.add(state("minecraft:smooth_stone",null));
        palette.add(state("minecraft:polished_andesite",null));palette.add(state("create:shaft","z"));
        palette.add(state("create:cogwheel","z"));root.put("palette",palette);
        var blocks=new ListTag();
        for(int x=0;x<9;x++)for(int z=0;z<9;z++)blocks.add(block(x,0,z,x==0||x==8||z==0||z==8?1:0));
        blocks.add(block(1,1,0,2));blocks.add(block(1,1,1,3));blocks.add(block(1,1,2,2));
        root.put("blocks",blocks);root.put("entities",new ListTag());
        Path output=Path.of("src/main/resources/assets/beltsnpieces/ponder/reinforced_belt.nbt");
        Files.createDirectories(output.getParent());NbtIo.writeCompressed(root,output);
        var verified=NbtIo.readCompressed(output,NbtAccounter.unlimitedHeap());
        if(verified.getList("blocks",Tag.TAG_COMPOUND).size()!=84 || verified.getList("palette",Tag.TAG_COMPOUND).size()!=4)
            throw new IllegalStateException("Ponder stage did not round-trip");
        System.out.println(output+": verified 9x6x9 stage, 81 base blocks and 3 drive/shaft blocks");
    }
}
