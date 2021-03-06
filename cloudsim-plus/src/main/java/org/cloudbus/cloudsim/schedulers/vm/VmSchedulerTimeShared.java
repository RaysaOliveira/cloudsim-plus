/*
 * Title: CloudSim Toolkit Description: CloudSim (Cloud Simulation) Toolkit for Modeling and
 * Simulation of Clouds Licence: GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2009-2012, The University of Melbourne, Australia
 */
package org.cloudbus.cloudsim.schedulers.vm;

import java.util.*;

import static java.util.stream.Collectors.toList;
import org.cloudbus.cloudsim.util.Log;
import org.cloudbus.cloudsim.resources.Pe;
import org.cloudbus.cloudsim.vms.Vm;

/**
 * VmSchedulerTimeShared is a Virtual Machine Monitor (VMM), also called Hypervisor,
 * that defines a policy to allocate one or more PEs from a PM to a VM, and allows sharing of PEs
 * by multiple VMs. <b>This class also implements 10% performance degradation due
 * to VM migration. It does not support over-subscription.</b>
 *
 * <p>Each host has to use is own instance of a VmScheduler that will so
 * schedule the allocation of host's PEs for VMs running on it.</p>
 *
 * <p>
 * It does not perform a preemption process in order to move running
 * VMs to the waiting list in order to make room for other already waiting
 * VMs to run. It just imposes there is not waiting VMs,
 * <b>oversimplifying</b> the scheduling, considering that for a given simulation
 * second <tt>t</tt>, the total processing capacity of the processor cores (in
 * MIPS) is equally divided by the VMs that are using them.
 * </p>
 *
 * <p>In processors enabled with <a href="https://en.wikipedia.org/wiki/Hyper-threading">Hyper-threading technology (HT)</a>,
 * it is possible to run up to 2 processes at the same physical CPU core.
 * However, this scheduler implementation
 * oversimplifies a possible HT feature by allowing several VMs to use a fraction of the MIPS capacity from
 * physical PEs, until that the total capacity of the virtual PE is allocated.
 * Consider that a virtual PE is requiring 1000 MIPS but there is no physical PE
 * with such a capacity. The scheduler will allocate these 1000 MIPS across several physical PEs,
 * for instance, by allocating 500 MIPS from PE 0, 300 from PE 1 and 200 from PE 2, totaling the 1000 MIPS required
 * by the virtual PE.
 * </p>
 *
 * <p>In a real hypervisor in a Host that has Hyper-threading CPU cores, two virtual PEs can be
 * allocated to the same physical PE, but a single virtual PE must be allocated to just one physical PE.</p>
 *
 * @author Rodrigo N. Calheiros
 * @author Anton Beloglazov
 * @since CloudSim Toolkit 1.0
 */
public class VmSchedulerTimeShared extends VmSchedulerAbstract {

    /**
     * The number of host's PEs in use.
     */
    private long pesInUse;

    /**
     * Creates a time-shared VM scheduler.
     *
     */
    public VmSchedulerTimeShared() {
        this(DEFAULT_VM_MIGRATION_CPU_OVERHEAD);
    }

    /**
     * Creates a time-shared VM scheduler, defining a CPU overhead for VM migration.
     *
     * @param vmMigrationCpuOverhead the percentage of Host's CPU usage increase when a
     * VM is migrating in or out of the Host. The value is in scale from 0 to 1 (where 1 is 100%).
     */
    public VmSchedulerTimeShared(final double vmMigrationCpuOverhead){
        super(vmMigrationCpuOverhead);
    }

    @Override
    public boolean allocatePesForVmInternal(Vm vm, final List<Double> mipsShareRequested) {
        if(!allocateMipsShareForVmInternal(vm, mipsShareRequested)) {
            return false;
        }

        updatePesAllocationForAllVms();
        return true;
    }

    /**
     * Try to allocate the MIPS requested by a VM
     * and update the {@link #getMipsMapRequested()}.
     *
     * @param vm the VM
     * @param mipsShareRequested the list of mips share requested by the vm
     * @return true if successful, false otherwise
     */
    protected boolean allocateMipsShareForVmInternal(Vm vm, List<Double> mipsShareRequested) {
        if (!isAllowedToAllocateMips(mipsShareRequested)) {
            return false;
        }

        setPesInUse(getPesInUse() + mipsShareRequested.size());
        allocateMipsShareForVm(vm, mipsShareRequested);

        return true;
    }

    /**
     * Performs the allocation of a MIPS List to a given VM.
     * The actual MIPS to be allocated to the VM may be reduced
     * if the VM is in migration, due to migration overhead.
     *
     * @param vm the VM to allocate MIPS to
     * @param mipsShareRequestedReduced the list of MIPS to allocate to the VM,
     * after it being adjusted by the {@link #getMipsShareRequestedReduced(Vm, List)} method.
     * @see #getMipsShareRequestedReduced(Vm, List)
     */
    protected void allocateMipsShareForVm(Vm vm, final List<Double> mipsShareRequestedReduced) {
        final List<Double> mipsShare = getMipsShareToAllocate(vm, mipsShareRequestedReduced);
        getMipsMapAllocated().put(vm, mipsShare);
    }

    /**
     * Update allocation of Host PEs for all VMs.
     */
    private void updatePesAllocationForAllVms() {
        clearAllocationOfPesForAllVms();
        getMipsMapAllocated().entrySet().forEach(this::allocatePesListForVm);
    }

    /**
     * Clear the allocation of any PE for all VMs in order to start a new allocation.
     * By this way, a PE that was previously allocated to a given VM will be released
     * and when the new allocation is performed, a different list of PEs can be alocated
     * that VM.
     * @see #updatePesAllocationForAllVms()
     */
    private void clearAllocationOfPesForAllVms() {
        getPeMap().clear();
        getHost().getPeList().forEach(pe -> pe.getPeProvisioner().deallocateResourceForAllVms());
    }

    /**
     * Allocates Host PEs for a given VM.
     * @param entry an entry from the {@link #getMipsMapAllocated()} containing a VM and
     *              the list of MIPS to be allocated for each of its PEs
     */
    private void allocatePesListForVm(Map.Entry<Vm, List<Double>> entry) {
        final Vm vm = entry.getKey();
        final Iterator<Pe> hostPesIterator = getWorkingPeList().iterator();
        //Iterate over the list of MIPS requested by each VM PE
        for (final double requestedMipsForVmPe : entry.getValue()) {
            final double allocatedMipsForVmPe = allocateMipsFromHostPesToGivenVirtualPe(vm, requestedMipsForVmPe, hostPesIterator);
            if(requestedMipsForVmPe > 0.1 && allocatedMipsForVmPe <= 0.1){
                logMipsUnavailable(vm, requestedMipsForVmPe, allocatedMipsForVmPe);
            }
        }
    }

    /**
     * Prints a log showing that the total requested MIPS is not available
     * and that just a fraction or zero MIPS were allocated.
     *
     * @param vm the VM requesting the MIPS
     * @param requestedMipsForVmPe the the MIPS requested for a vPE
     * @param allocatedMipsForVmPe the actually allocated MIPS for the vPE
     */
    private void logMipsUnavailable(final Vm vm, final double requestedMipsForVmPe, final double allocatedMipsForVmPe) {
        final String msg = allocatedMipsForVmPe > 0 ?
                String.format("Only %.0f MIPS were allocated.", allocatedMipsForVmPe)
                : "No MIPS were allocated.";
        Log.printFormattedLine(
                "%.2f: %s: %s is requiring a total of %.0f MIPS but the PEs of %s\n\t currently don't have such an available MIPS amount. %s",
                getHost().getSimulation().clock(),
                getClass().getSimpleName(), vm,
                requestedMipsForVmPe, getHost(), msg);
    }

    /**
     * Try to allocate MIPS from one or more Host PEs to a specific Virtual PE (PE of a VM).
     *
     * @param vm the VM to try to find Host PEs for one of its Virtual PEs
     * @param requestedMipsForVmPe the amount of MIPS requested by such a VM PE
     * @param hostPesIterator a interator over the PEs of the {@link #getHost() Host} that the schedler will
     *                        iterate over to allocate PEs for a VM
     * @return the total MIPS allocated from one or more Host PEs for the requested VM PE
     *
     * @TODO @author manoelcampos The method implementation must to be checked. See the comments inside.
     * Probably there was performed an oversimplification when implementing this method,
     * as it is made for CloudletSchedulerTimeShared class.
     *
     */
    private double allocateMipsFromHostPesToGivenVirtualPe(Vm vm, final double requestedMipsForVmPe, Iterator<Pe> hostPesIterator) {
        if(requestedMipsForVmPe <= 0){
            return 0;
        }

        double allocatedMipsForVmPe = 0;
        /*
        * While all the requested MIPS for the VM PE was not allocated, try to find a Host PE
        * with that MIPS amount available.
        */
        while (allocatedMipsForVmPe <= 0 && hostPesIterator.hasNext()) {
            Pe selectedHostPe = hostPesIterator.next();
             if(allocateAllVmPeRequestedMipsFromHostPe(vm, selectedHostPe, requestedMipsForVmPe)){
                allocatedMipsForVmPe = requestedMipsForVmPe;
             } else {
                allocatedMipsForVmPe += allocatedAvailableMipsFromHostPeToVirtualPe(vm, selectedHostPe);
             }
        }

        return allocatedMipsForVmPe;
    }

    /**
     * Try to allocate the MIPS available in a given Physical PE
     * to a vPE. This method is used when the MIPS requested
     * by a vPE is not entirely available at a Physical PE.
     * This all, this method allocates the available capacity to the vPE.
     *
     * @param vm the VM to allocate MIPS from one of its vPEs.
     * @param hostPe the Physical PE to allocated MIPS from
     * @return true if all requested MIPS of the vPE is available at the physical PE
     * and was allocated, false otherwise
     *
     * @see #allocateAllVmPeRequestedMipsFromHostPe(org.cloudbus.cloudsim.vms.Vm, org.cloudbus.cloudsim.resources.Pe, double)
     */
    private double allocatedAvailableMipsFromHostPeToVirtualPe(Vm vm, Pe hostPe) {
        final double availableMips = getAvailableMipsFromHostPe(hostPe);
        if (availableMips <= 0){
           return 0;
        }

        /*
        * If the selected Host PE doesn't have the available MIPS requested by the current
        * vPE, allocate the MIPS that is available in that PE for the vPE
        * and try to find another Host PE to allocate the remaining MIPS required by the
        * current vPE.
        */
        allocateMipsFromHostPeForVm(vm, hostPe, availableMips);
        return availableMips;
    }

    /**
     * Try to allocate all the MIPS requested by a Virtual PE from a given Physical PE.
     *
     * @param vm the VM to allocate MIPS from one of its vPEs.
     * @param hostPe the Physical PE to allocated MIPS from
     * @param requestedMipsForVmPe the MIPS requested by the vPE
     * @return true if all requested MIPS of the vPE is available at the physical PE
     * and was allocated, false otherwise
     *
     * @todo If the selected Host PE has enough available MIPS that is requested by the
     * current VM PE (Virtual PE, vPE or vCore), allocate that MIPS from that Host PE for that vPE.
     * For each next vPE, in case the same previous selected Host PE yet
     * has available MIPS to allocate to it, that Host PE will be allocated
     * to that next vPE. However, for the best of my knowledge,
     * in real scheduling, it is not possible to allocate
     * more than one VM to the same CPU core,
     * even in CPUs with Hyper-Threading technology.
     * The last picture in the following article makes it clear:
     * https://support.rackspace.com/how-to/numa-vnuma-and-cpu-scheduling/
     */
    private boolean allocateAllVmPeRequestedMipsFromHostPe(Vm vm, Pe hostPe, final double requestedMipsForVmPe) {
        if (getAvailableMipsFromHostPe(hostPe) >= requestedMipsForVmPe) {
            allocateMipsFromHostPeForVm(vm, hostPe, requestedMipsForVmPe);
            return true;
        }

        return false;
    }

    private long getAvailableMipsFromHostPe(Pe hostPe) {
        return hostPe.getPeProvisioner().getAvailableResource();
    }

    /**
     * Allocates a given amount of MIPS from a specific PE for a given VM.
     * @param vm the VM to allocate the MIPS from a given PE
     * @param pe the PE that will have MIPS allocated to the VM
     * @param mipsToAllocate the amount of MIPS from the PE that have to be allocated to the VM
     */
    private void allocateMipsFromHostPeForVm(Vm vm, Pe pe, double mipsToAllocate) {
        pe.getPeProvisioner().allocateResourceForVm(vm, (long)mipsToAllocate);
        getPeMap().getOrDefault(vm, new ArrayList<>()).add(pe);
    }

    @Override
    public boolean isSuitableForVm(List<Double> vmMipsList) {
        return isAllowedToAllocateMips(vmMipsList);
    }

    /**
     * Gets the actual MIPS that will be allocated to each vPE (Virtual PE),
     * considering the VM migration status.
     * If the VM is in migration, this will cause overhead, reducing
     * the amount of MIPS allocated to the VM.
     *
     * @param vm the VM requesting allocation of MIPS
     * @param mipsShareRequested the list of MIPS requested for each vPE
     * @return the List of MIPS allocated to the VM
     */
    protected List<Double> getMipsShareToAllocate(Vm vm, List<Double> mipsShareRequested) {
        return getMipsShareToAllocate(vm, mipsShareRequested, percentOfMipsToRequest(vm));
    }

    /**
     * Gets the actual MIPS that will be allocated to each vPE (Virtual PE),
     * considering the VM migration status.
     * If the VM is in migration, this will cause overhead, reducing
     * the amount of MIPS allocated to the VM.
     *
     * @param vm the VM requesting allocation of MIPS
     * @param mipsShareRequested the list of MIPS requested for each vPE
     * @param scalingFactor the factor that will be used to reduce the amount of MIPS
     * allocated to each vPE (which is a percentage value between [0 .. 1])
     * @return the List of MIPS allocated to the VM
     */
    protected List<Double> getMipsShareToAllocate(Vm vm, List<Double> mipsShareRequested, double scalingFactor) {
        return mipsShareRequested
                .stream()
                .map(mips -> mips*scalingFactor)
                .collect(toList());
    }

    @Override
    protected void deallocatePesFromVmInternal(Vm vm, int pesToRemove) {
        final int removedPes = removePesFromMap(vm, getMipsMapRequested(), pesToRemove);
        setPesInUse(pesInUse - removedPes);
        removePesFromMap(vm, getMipsMapAllocated(), pesToRemove);

        for (final Map.Entry<Vm, List<Double>> entry : getMipsMapRequested().entrySet()) {
            allocateMipsShareForVmInternal(entry.getKey(), entry.getValue());
        }

        updatePesAllocationForAllVms();
    }

    /**
     * Releases PEs allocated to all the VMs.
     *
     * @pre $none
     * @post $none
     */
    @Override
    public void deallocatePesForAllVms() {
        super.deallocatePesForAllVms();
        getMipsMapRequested().clear();
        setPesInUse(0);
    }

    /**
     * Sets the number of PEs in use.
     *
     * @param pesInUse the new pes in use
     */
    protected void setPesInUse(long pesInUse) {
        this.pesInUse = Math.max(pesInUse, 0);
    }

    /**
     * Gets the number of PEs in use.
     *
     * @return the pes in use
     */
    protected long getPesInUse() {
        return pesInUse;
    }

}
