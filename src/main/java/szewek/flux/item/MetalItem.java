package szewek.flux.item;

import net.minecraft.item.Item;
import szewek.flux.util.Metal;

public final class MetalItem extends Item {
	public Metal metal;

	public MetalItem(Properties properties) {
		super(properties);
	}

	public final MetalItem withMetal(Metal metal) {
		if (this.metal == null) {
			this.metal = metal;
		}
		return this;
	}

}