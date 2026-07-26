package com.boostermod.mixin;

import com.boostermod.villager.ArmedVillagerEquipment;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Villager.class)
public abstract class VillagerArmedEquipmentMixin {
    @Inject(method = "wantsToPickUp", at = @At("RETURN"), cancellable = true)
    private void boostermod$wantsArmedGear(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) {
            return;
        }
        Villager villager = (Villager) (Object) this;
        if (ArmedVillagerEquipment.wantsArmedGear(villager, stack)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "pickUpItem", at = @At("HEAD"), cancellable = true)
    private void boostermod$equipArmedGear(ItemEntity itemEntity, CallbackInfo ci) {
        Villager villager = (Villager) (Object) this;
        if (ArmedVillagerEquipment.tryEquipFromGround(villager, itemEntity)) {
            ci.cancel();
        }
    }
}
