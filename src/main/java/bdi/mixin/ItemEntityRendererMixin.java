package bdi.mixin;

import bdi.util.ItemEntityRotator;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.SkullBlock;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.ItemEntityRenderer;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.json.ModelTransformation;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.AliasedBlockItem;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tag.FluidTags;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3f;
import net.minecraft.util.shape.VoxelShape;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Random;

@Mixin(ItemEntityRenderer.class)
public abstract class ItemEntityRendererMixin extends EntityRenderer<ItemEntity> {

    @Shadow
    @Final
    private Random random;
    @Shadow
    @Final
    private ItemRenderer itemRenderer;

    @Shadow
    protected abstract int getRenderedAmount(ItemStack stack);

    private ItemEntityRendererMixin(EntityRendererFactory.Context dispatcher) {
        super(dispatcher);
    }

    @Inject(at = @At("RETURN"), method = "<init>")
    private void onConstructor(EntityRendererFactory.Context context, CallbackInfo ci) {
        this.shadowRadius = 0;
    }

    @Inject(at = @At("HEAD"), method = "render", cancellable = true)
    private void render(ItemEntity entity, float f, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumerProvider, int i, CallbackInfo callback) {
        ItemStack itemStack = entity.getStack();

        int seed = itemStack.isEmpty() ? 187 : Item.getRawId(itemStack.getItem()) + itemStack.getDamage() + entity.getId();
        this.random.setSeed(seed);

        matrices.push();
        BakedModel bakedModel = this.itemRenderer.getHeldItemModel(itemStack, entity.world, null, seed);
        boolean hasDepth = bakedModel.hasDepth();

        int renderCount = this.getRenderedAmount(itemStack);
        ItemEntityRotator rotator = (ItemEntityRotator) entity;

        final var item = entity.getStack().getItem();
        final boolean itemIsActualBlock = item instanceof BlockItem && !(item instanceof AliasedBlockItem);
        //final boolean shouldNotBeRotated = itemIsActualBlock && ((BlockItem)item).getBlock().getOutlineShape(((BlockItem)item).getBlock().getDefaultState(), entity.world, entity.getBlockPos(), ShapeContext.absent()).getMax(Direction.Axis.Y) <= 0.5;

        float scaleX = bakedModel.getTransformation().ground.scale.getX();
        float scaleY = bakedModel.getTransformation().ground.scale.getY();
        float scaleZ = bakedModel.getTransformation().ground.scale.getZ();

        float groundDistance = itemIsActualBlock ? 0 : (float) (0.125 - 0.0625 * scaleZ);

        if(!itemIsActualBlock) groundDistance -= (renderCount - 1) * 0.05 * scaleZ;
        matrices.translate(0, -groundDistance, 0);

        matrices.translate(0, 0.125f, 0);
        float angle = entity.isOnGround() ? (float) rotator.getRotation().x : (float) ((entity.getItemAge() + tickDelta) * MathHelper.clamp(entity.getVelocity().length() * 0.75, 0.015, 10));

        final double twoPi = Math.PI * 2;
        final double halfPi = Math.PI * 0.5;
        final double threeHalfPi = Math.PI * 1.5;
        final double turnSpeed = 0.15;

        if (angle >= twoPi) angle -= twoPi;

        if (entity.isOnGround() && !(angle == 0 || angle == (float) Math.PI)) {
            if (angle > Math.PI) {
                if (angle > threeHalfPi) angle += turnSpeed;
                else {
                    angle -= turnSpeed;
                }
            } else {
                if (angle > halfPi) {
                    angle += turnSpeed;
                    if (angle > Math.PI) angle = (float) Math.PI;
                } else angle -= turnSpeed;
            }

            if (angle < 0) angle = 0;
            if (angle > twoPi) angle = 0;
        }

        matrices.multiply(Vec3f.POSITIVE_X.getRadialQuaternion((float) (angle + halfPi)));
        rotator.setRotation(new Vec3d(angle, 0, 0));
        matrices.translate(0, -0.125f, 0);


        matrices.translate(0, 0, ((0.09375 - (renderCount * 0.1)) * 0.5) * scaleZ);

        if(itemIsActualBlock) matrices.translate(0, -0.25 * scaleY, 0);

        float x;
        float y;

        // render each item in the stack on the ground (higher stack count == more items displayed)
        for (int u = 0; u < renderCount; ++u) {
            matrices.push();

            // random positioning for rendered items, is especially seen in 64 block stacks on the ground
            if (u > 0) {
                if (hasDepth) {
                    x = (this.random.nextFloat() * 2.0F - 1.0F) * 0.15F;
                    y = (this.random.nextFloat() * 2.0F - 1.0F) * 0.15F;
                    float z = (this.random.nextFloat() * 2.0F - 1.0F) * 0.15F;
                    matrices.translate(x, y, z);
                } else {
                    matrices.translate(0, 0.125f, 0.0D);
                    matrices.multiply(Vec3f.POSITIVE_Z.getRadialQuaternion((this.random.nextFloat() - 0.5f)));
                    matrices.translate(0, -0.125f, 0.0D);
                }
            }

            // render item
            this.itemRenderer.renderItem(itemStack, ModelTransformation.Mode.GROUND, false, matrices, vertexConsumerProvider, i, OverlayTexture.DEFAULT_UV, bakedModel);

            // end
            matrices.pop();

            // translate based on scale, which gives vertical layering to high stack count items
            if (!hasDepth) {
                matrices.translate(0, 0, 0.1F * scaleZ);
            }
        }

        // end
        matrices.pop();
        super.render(entity, f, tickDelta, matrices, vertexConsumerProvider, i);
        callback.cancel();
    }
}
