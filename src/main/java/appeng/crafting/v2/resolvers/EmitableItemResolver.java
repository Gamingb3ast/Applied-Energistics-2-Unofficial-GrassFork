package appeng.crafting.v2.resolvers;

import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;

import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.crafting.MECraftingInventory;
import appeng.crafting.v2.CraftingContext;
import appeng.crafting.v2.CraftingRequest;
import appeng.me.cluster.implementations.CraftingCPUCluster;

public class EmitableItemResolver implements CraftingRequestResolver<IAEItemStack> {

    public static class EmitItemTask extends CraftingTask<IAEItemStack> {

        public EmitItemTask(CraftingRequest<IAEItemStack> request) {
            super(request, CraftingTask.PRIORITY_CRAFTING_EMITTER); // conjure items for calculations out of thin air as
                                                                    // a last
        }

        @Override
        public StepOutput calculateOneStep(CraftingContext context) {
            if (request.remainingToProcess <= 0) {
                state = State.SUCCESS;
                return new StepOutput(Collections.emptyList());
            }
            // Assume items will be generated, triggered by the emitter
            request.fulfill(this, request.stack.copy().setStackSize(request.remainingToProcess), context);
            state = State.SUCCESS;
            return new StepOutput(Collections.emptyList());
        }

        @Override
        public long partialRefund(CraftingContext context, long amount) {
            // no-op: items were simulated to be emitted, so there's nothing to refund
            return amount;
        }

        @Override
        public void fullRefund(CraftingContext context) {
            // no-op: items were simulated to be emitted, so there's nothing to refund
        }

        @Override
        public void populatePlan(IItemList<IAEItemStack> targetPlan) {
            targetPlan.addRequestable(request.stack.copy());
        }

        @Override
        public void startOnCpu(CraftingContext context, CraftingCPUCluster cpuCluster,
                MECraftingInventory craftingInv) {
            cpuCluster.addEmitable(this.request.stack.copy());
        }

        @Override
        public String toString() {
            return "EmitItemTask{" + "request=" + request + ", priority=" + priority + ", state=" + state + '}';
        }
    }

    @Nonnull
    @Override
    public List<CraftingTask> provideCraftingRequestResolvers(@Nonnull CraftingRequest<IAEItemStack> request,
            @Nonnull CraftingContext context) {
        if (context.craftingGrid.canEmitFor(request.stack)) {
            return Collections.singletonList(new EmitItemTask(request));
        } else {
            return Collections.emptyList();
        }
    }
}
