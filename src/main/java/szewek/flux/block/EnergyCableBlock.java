package szewek.flux.block;

import net.minecraft.block.BlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.IBlockReader;
import szewek.flux.F;

public final class EnergyCableBlock extends AbstractCableBlock {

	public EnergyCableBlock(Properties properties) {
		super(properties);
	}

	@Override
	public TileEntity createTileEntity(BlockState state, IBlockReader world) {
		return F.Tiles.ENERGY_CABLE.create();
	}
}