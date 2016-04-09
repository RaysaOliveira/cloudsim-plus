/*
 * Title:        CloudSim Toolkit
 * Description:  CloudSim (Cloud Simulation) Toolkit for Modeling and Simulation of Clouds
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2009-2012, The University of Melbourne, Australia
 */
package org.cloudbus.cloudsim.network.datacenter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.cloudbus.cloudsim.Cloudlet;

import org.cloudbus.cloudsim.CloudletSimple;
import org.cloudbus.cloudsim.DatacenterCharacteristics;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.CloudSimTags;
import org.cloudbus.cloudsim.core.SimEntity;
import org.cloudbus.cloudsim.core.SimEvent;
import org.cloudbus.cloudsim.distributions.UniformDistr;
import org.cloudbus.cloudsim.lists.VmList;

/**
 * NetDatacentreBroker represents a broker acting on behalf of Datacenter
 * provider. It hides VM management, as vm creation, submission of cloudlets to
 * these VMs and destruction of VMs. <br/>
 * <tt>NOTE</tt>: This class is an example only. It works on behalf of a
 * provider not for users. One has to implement interaction with user broker to
 * this broker.
 *
 * @author Saurabh Kumar Garg
 * @since CloudSim Toolkit 3.0
 * @todo The class is not a broker acting on behalf of users, but on behalf of a
 * provider. Maybe this distinction would be explicit by different class
 * hierarchy, such as UserDatacenterBroker and ProviderDatacenterBroker.
 */
public class NetDatacenterBroker extends SimEntity {

    // TODO: remove unnecessary variables
    /**
     * The list of submitted VMs.
     */
    private List<? extends NetworkVm> vmList;

    /**
     * The list of created VMs.
     */
    private List<? extends NetworkVm> vmsCreatedList;

    /**
     * The list of submitted {@link NetworkCloudlet NetworkCloudlets}.
     */
    private List<? extends NetworkCloudlet> cloudletList;

    /**
     * The list of submitted {@link AppCloudlet AppCloudlets}.
     */
    private List<? extends AppCloudlet> appCloudletList;

    /**
     * The list of submitted {@link AppCloudlet AppCloudlets}.
     *
     * @todo attribute appears to be redundant with {@link #appCloudletList}
     */
    private final Map<Integer, Integer> appCloudletReceived;

    /**
     * * The list of submitted {@link Cloudlet Cloudlets}.
     */
    private List<? extends CloudletSimple> cloudletSubmittedList;

    /**
     * * The list of received {@link Cloudlet Cloudlets}.
     *
     * @todo attribute appears to be redundant with
     * {@link #cloudletSubmittedList}
     */
    private List<? extends CloudletSimple> cloudletReceivedList;

    /**
     * The number of submitted cloudlets.
     */
    private int cloudletsSubmitted;

    /**
     * The number of VMs requested.
     */
    private int numberOfRequestedVms;

    /**
     * The acks sent to VMs.
     */
    private int numberOfAcksSentToVms;

    /**
     * The number of VMs destroyed.
     */
    private int numberOfDestroyedVms;

    /**
     * The list of datacenter IDs.
     */
    private List<Integer> datacenterIdsList;

    /**
     * The datacenter requested IDs list.
     *
     * @todo attribute appears to be redundant with {@link #datacenterIdsList}
     */
    private List<Integer> datacenterRequestedIdsList;

    /**
     * The VMs to datacenters map where each key is a VM id and the
     * corresponding value is the datacenter where the VM is placed.
     */
    private Map<Integer, Integer> vmsToDatacentersMap;

    /**
     * The datacenter characteristics map where each key is the datacenter id
     * and each value is the datacenter itself.
     */
    private Map<Integer, DatacenterCharacteristics> datacenterCharacteristicsList;

    public static NetworkDatacenter linkDC;

    public boolean createVmFlag = true;

    public static int cachedCloudlet = 0;

    /**
     * Creates a new DatacenterBroker object.
     *
     * @param name name to be associated with this entity
     *
     * @throws Exception the exception
     *
     * @pre name != null
     * @post $none
     */
    public NetDatacenterBroker(String name) throws Exception {
        super(name);

        setVmList(new ArrayList<NetworkVm>());
        setVmsCreatedList(new ArrayList<>());
        setCloudletList(new ArrayList<>());
        setAppCloudletList(new ArrayList<AppCloudlet>());
        setCloudletSubmittedList(new ArrayList<>());
        setCloudletReceivedList(new ArrayList<>());
        appCloudletReceived = new HashMap<>();

        cloudletsSubmitted = 0;
        setNumberOfRequestedVms(0);
        setNumberOfAcksSentToVms(0);
        setNumberOfDestroyedVms(0);

        setDatacenterIdsList(new LinkedList<>());
        setDatacenterRequestedIdsList(new ArrayList<>());
        setVmsToDatacentersMap(new HashMap<>());
        setDatacenterCharacteristicsList(new HashMap<>());
    }

    /**
     * Sends to the broker the list with virtual machines that must be created.
     *
     * @param list the list
     *
     * @pre list !=null
     * @post $none
     */
    public void submitVmList(List<? extends NetworkVm> list) {
        getVmList().addAll(list);
    }

    /**
     * Sends to the broker the list of cloudlets.
     *
     * @param list the list
     *
     * @pre list !=null
     * @post $none
     */
    public void submitCloudletList(List<? extends NetworkCloudlet> list) {
        getCloudletList().addAll(list);
    }

    public static void setLinkDC(NetworkDatacenter aLinkDC) {
        linkDC = aLinkDC;
    }

    /**
     * Processes events available for this Broker.
     *
     * @param ev a SimEvent object
     *
     * @pre ev != null
     * @post $none
     */
    @Override
    public void processEvent(SimEvent ev) {
        switch (ev.getTag()) {
            // Resource characteristics request
            case CloudSimTags.RESOURCE_CHARACTERISTICS_REQUEST:
                processDatacenterCharacteristicsRequest(ev);
            break;
            // Resource characteristics answer
            case CloudSimTags.RESOURCE_CHARACTERISTICS:
                processDatacenterCharacteristics(ev);
            break;
            // A finished cloudlet returned
            case CloudSimTags.CLOUDLET_RETURN:
                processCloudletReturn(ev);
            break;
            // if the simulation finishes
            case CloudSimTags.END_OF_SIMULATION:
                shutdownEntity();
            break;
            case CloudSimTags.NextCycle:
                if (NetworkConstants.autoCreateVmsInNetDatacenterBroker) {
                    createVmsInDatacenterBase(linkDC.getId());
                }
            break;
            // other unknown tags are processed by this method
            default:
                processOtherEvent(ev);
            break;
        }
    }

    /**
     * Processes the return of a request for the characteristics of a
     * Datacenter.
     *
     * @param ev a SimEvent object
     *
     * @pre ev != $null
     * @post $none
     */
    protected void processDatacenterCharacteristics(SimEvent ev) {
        DatacenterCharacteristics characteristics = (DatacenterCharacteristics) ev.getData();
        getDatacenterCharacteristicsList().put(characteristics.getId(), characteristics);

        if (getDatacenterCharacteristicsList().size() == getDatacenterIdsList().size()) {
            setDatacenterRequestedIdsList(new ArrayList<>());
            createVmsInDatacenterBase(getDatacenterIdsList().get(0));
        }
    }

    /**
     * Processes a request for the characteristics of a Datacenter.
     *
     * @param ev a SimEvent object
     *
     * @pre ev != $null
     * @post $none
     */
    protected void processDatacenterCharacteristicsRequest(SimEvent ev) {
        setDatacenterIdsList(CloudSim.getCloudResourceList());
        setDatacenterCharacteristicsList(new HashMap<>());

        Log.printConcatLine(CloudSim.clock(), ": ", getName(), ": Cloud Datacenter List received with ",
                getDatacenterIdsList().size(), " datacenter(s)");

        for (Integer datacenterId : getDatacenterIdsList()) {
            sendNow(datacenterId, CloudSimTags.RESOURCE_CHARACTERISTICS, getId());
        }
    }

    /**
     * Processes the ack received due to a request for VM creation.
     *
     * @param ev a SimEvent object
     *
     * @pre ev != null
     * @post $none
     */
    /**
     * Processes a cloudlet return event.
     *
     * @param ev a SimEvent object
     *
     * @pre ev != $null
     * @post $none
     */
    protected void processCloudletReturn(SimEvent ev) {
        Cloudlet cloudlet = (CloudletSimple) ev.getData();
        getCloudletReceivedList().add(cloudlet);
        cloudletsSubmitted--;
        // all cloudlets executed
        if (getCloudletList().isEmpty() && cloudletsSubmitted == 0 && NetworkConstants.iteration > 10) {
            Log.printConcatLine(CloudSim.clock(), ": ", getName(), ": All Cloudlets executed. Finishing...");
            clearDatacenters();
            finishExecution();
        } 
        // some cloudlets haven't finished yet
        else if (getAppCloudletList().size() > 0 && cloudletsSubmitted == 0) {
            // all the cloudlets sent finished. It means that some bount
            // cloudlet is waiting its VM be created
            clearDatacenters();
            createVmsInDatacenterBase(0);
        }
    }

    /**
     * Processes non-default received events that aren't processed by the
     * {@link #processEvent(org.cloudbus.cloudsim.core.SimEvent)} method. This
     * method should be overridden by subclasses in other to process new defined
     * events.
     *
     * @param ev a SimEvent object
     *
     * @pre ev != null
     * @post $none
     */
    protected void processOtherEvent(SimEvent ev) {
        if (ev == null) {
            Log.printConcatLine(getName(), ".processOtherEvent(): Error - an event is null.");
            return;
        }

        Log.printConcatLine(getName(), ".processOtherEvent(): ",
                "Error - event unknown by this DatacenterBroker.");
    }

    /**
     * Creates virtual machines in a datacenter and submit/schedule cloudlets to
     * them.
     *
     * @param datacenterId Id of the Datacenter to create the VMs
     *
     * @pre $none
     * @post $none
     */
    protected void createVmsInDatacenterBase(int datacenterId) {
        // send as much vms as possible for this datacenter before trying the
        // next one
        int requestedVms = 0;

        // All host will have two VMs (assumption) VM is the minimum unit
        if (createVmFlag) {
            createVMs(datacenterId);
            createVmFlag = false;
        }

        // generate Application execution Requests
        for (int i = 0; i < 100; i++) {
            this.getAppCloudletList().add(
                    new WorkflowApp(AppCloudlet.APP_WORKFLOW, 
                    NetworkConstants.currentAppId, 0, 0, getId()));
            NetworkConstants.currentAppId++;

        }
        int k = 0;

        // schedule the application on VMs
        for (AppCloudlet app : this.getAppCloudletList()) {
            List<Integer> vmIds = new ArrayList<>();
            int numVms = linkDC.getVmList().size();
            UniformDistr ufrnd = new UniformDistr(0, numVms, 5);
            for (int i = 0; i < app.numberOfVMs; i++) {
                int vmId = (int) ufrnd.sample();
                vmIds.add(vmId);
            }

            if (!vmIds.isEmpty()) {
                app.createCloudletList(vmIds);
                for (int i = 0; i < app.numberOfVMs; i++) {
                    app.networkCloudletList.get(i).setUserId(getId());
                    appCloudletReceived.put(app.appId, app.numberOfVMs);
                    this.getCloudletSubmittedList().add(app.networkCloudletList.get(i));
                    cloudletsSubmitted++;

                    // Sending cloudlet
                    sendNow(
                        getVmsToDatacentersMap().get(this.getVmList().get(0).getId()),
                        CloudSimTags.CLOUDLET_SUBMIT,
                        app.networkCloudletList.get(i));
                }
                System.out.println("app" + (k++));
            }
        }
        
        setAppCloudletList(new ArrayList<AppCloudlet>());
        if (NetworkConstants.iteration < 10) {
            NetworkConstants.iteration++;
            this.schedule(getId(), NetworkConstants.nextTime, CloudSimTags.NextCycle);
        }

        setNumberOfRequestedVms(requestedVms);
        setNumberOfAcksSentToVms(0);
    }

    /**
     * Creates virtual machines in a datacenter
     *
     * @param datacenterId The id of the datacenter where to create the VMs.
     */
    private void createVMs(int datacenterId) {
        // two VMs per host
        int numberOfVms = linkDC.getHostList().size() * NetworkConstants.maxVmsPerHost;
        for (int i = 0; i < numberOfVms; i++) {
            int vmid = i;
            int mips = 1;
            long size = 10000; // image size (MB)
            int ram = 512; // vm memory (MB)
            long bw = 1000;
            int pesNumber = NetworkConstants.hostPEs / NetworkConstants.maxVmsPerHost;
            String vmm = "Xen"; // VMM name

            // create VM
            NetworkVm vm = new NetworkVm(
                    vmid, getId(), mips, pesNumber,
                    ram, bw, size, vmm,
                    new NetworkCloudletSpaceSharedScheduler());
            linkDC.processVmCreateNetwork(vm);
            // add the VM to the vmList
            getVmList().add(vm);
            getVmsToDatacentersMap().put(vmid, datacenterId);

            NetworkVm foundVm = VmList.getById(getVmList(), vmid);
            getVmsCreatedList().add(foundVm);
        }
    }

    /**
     * Sends request to destroy all VMs running on the datacenter.
     *
     * @pre $none
     * @post $none /** Destroy the virtual machines running in datacenters.
     *
     * @pre $none
     * @post $none
     */
    protected void clearDatacenters() {
        for (Vm vm : getVmsCreatedList()) {
            Log.printConcatLine(CloudSim.clock(), ": ", getName(), ": Destroying VM #", vm.getId());
            sendNow(getVmsToDatacentersMap().get(vm.getId()), CloudSimTags.VM_DESTROY, vm);
        }

        getVmsCreatedList().clear();
    }

    /**
     * Sends an internal event communicating the end of the simulation.
     *
     * @pre $none
     * @post $none
     */
    private void finishExecution() {
        sendNow(getId(), CloudSimTags.END_OF_SIMULATION);
    }

    @Override
    public void shutdownEntity() {
        Log.printConcatLine(getName(), " is shutting down...");
    }

    @Override
    public void startEntity() {
        Log.printConcatLine(getName(), " is starting...");
        schedule(getId(), 0, CloudSimTags.RESOURCE_CHARACTERISTICS_REQUEST);
    }

    /**
     * Gets the vm list.
     *
     * @param <T> the generic type
     * @return the vm list
     */
    @SuppressWarnings("unchecked")
    public <T extends NetworkVm> List<T> getVmList() {
        return (List<T>) vmList;
    }

    /**
     * Sets the vm list.
     *
     * @param <T> the generic type
     * @param vmList the new vm list
     */
    protected final <T extends NetworkVm> void setVmList(List<T> vmList) {
        this.vmList = vmList;
    }

    /**
     * Gets the cloudlet list.
     *
     * @param <T> the generic type
     * @return the cloudlet list
     */
    @SuppressWarnings("unchecked")
    public <T extends NetworkCloudlet> List<T> getCloudletList() {
        return (List<T>) cloudletList;
    }

    /**
     * Sets the cloudlet list.
     *
     * @param <T> the generic type
     * @param cloudletList the new cloudlet list
     */
    protected final <T extends NetworkCloudlet> void setCloudletList(List<T> cloudletList) {
        this.cloudletList = cloudletList;
    }

    @SuppressWarnings("unchecked")
    public <T extends AppCloudlet> List<T> getAppCloudletList() {
        return (List<T>) appCloudletList;
    }

    public final <T extends AppCloudlet> void setAppCloudletList(List<T> appCloudletList) {
        this.appCloudletList = appCloudletList;
    }

    /**
     * Gets the cloudlet submitted list.
     *
     * @param <T> the generic type
     * @return the cloudlet submitted list
     */
    @SuppressWarnings("unchecked")
    public <T extends Cloudlet> List<T> getCloudletSubmittedList() {
        return (List<T>) cloudletSubmittedList;
    }

    /**
     * Sets the cloudlet submitted list.
     *
     * @param <T> the generic type
     * @param cloudletSubmittedList the new cloudlet submitted list
     */
    protected final <T extends CloudletSimple> void setCloudletSubmittedList(List<T> cloudletSubmittedList) {
        this.cloudletSubmittedList = cloudletSubmittedList;
    }

    /**
     * Gets the cloudlet received list.
     *
     * @param <T> the generic type
     * @return the cloudlet received list
     */
    @SuppressWarnings("unchecked")
    public <T extends Cloudlet> List<T> getCloudletReceivedList() {
        return (List<T>) cloudletReceivedList;
    }

    /**
     * Sets the cloudlet received list.
     *
     * @param <T> the generic type
     * @param cloudletReceivedList the new cloudlet received list
     */
    protected final <T extends CloudletSimple> void setCloudletReceivedList(List<T> cloudletReceivedList) {
        this.cloudletReceivedList = cloudletReceivedList;
    }

    /**
     * Gets the vm list.
     *
     * @param <T> the generic type
     * @return the vm list
     */
    @SuppressWarnings("unchecked")
    public <T extends NetworkVm> List<T> getVmsCreatedList() {
        return (List<T>) vmsCreatedList;
    }

    /**
     * Sets the vm list.
     *
     * @param <T> the generic type
     * @param vmsCreatedList the vms created list
     */
    protected final <T extends NetworkVm> void setVmsCreatedList(List<T> vmsCreatedList) {
        this.vmsCreatedList = vmsCreatedList;
    }

    /**
     * Gets the vms requested.
     *
     * @return the vms requested
     */
    protected int getNumberOfRequestedVms() {
        return numberOfRequestedVms;
    }

    /**
     * Sets the vms requested.
     *
     * @param numberOfRequestedVms the new vms requested
     */
    protected final void setNumberOfRequestedVms(int numberOfRequestedVms) {
        this.numberOfRequestedVms = numberOfRequestedVms;
    }

    /**
     * Gets the vms acks.
     *
     * @return the vms acks
     */
    protected int getNumberOfAcksSentToVms() {
        return numberOfAcksSentToVms;
    }

    /**
     * Sets the vms acks.
     *
     * @param numberOfAcksSentToVms the new vms acks
     */
    protected final void setNumberOfAcksSentToVms(int numberOfAcksSentToVms) {
        this.numberOfAcksSentToVms = numberOfAcksSentToVms;
    }

    /**
     * Increment vms acks.
     */
    protected void incrementVmsAcks() {
        numberOfAcksSentToVms++;
    }

    /**
     * Gets the vms destroyed.
     *
     * @return the vms destroyed
     */
    protected int getNumberOfDestroyedVms() {
        return numberOfDestroyedVms;
    }

    /**
     * Sets the vms destroyed.
     *
     * @param numberOfDestroyedVms the new vms destroyed
     */
    protected final void setNumberOfDestroyedVms(int numberOfDestroyedVms) {
        this.numberOfDestroyedVms = numberOfDestroyedVms;
    }

    /**
     * Gets the datacenter ids list.
     *
     * @return the datacenter ids list
     */
    protected List<Integer> getDatacenterIdsList() {
        return datacenterIdsList;
    }

    /**
     * Sets the datacenter ids list.
     *
     * @param datacenterIdsList the new datacenter ids list
     */
    protected final void setDatacenterIdsList(List<Integer> datacenterIdsList) {
        this.datacenterIdsList = datacenterIdsList;
    }

    /**
     * Gets the vms to datacenters map.
     *
     * @return the vms to datacenters map
     */
    protected Map<Integer, Integer> getVmsToDatacentersMap() {
        return vmsToDatacentersMap;
    }

    /**
     * Sets the vms to datacenters map.
     *
     * @param vmsToDatacentersMap the vms to datacenters map
     */
    protected final void setVmsToDatacentersMap(Map<Integer, Integer> vmsToDatacentersMap) {
        this.vmsToDatacentersMap = vmsToDatacentersMap;
    }

    /**
     * Gets the datacenter characteristics list.
     *
     * @return the datacenter characteristics list
     */
    protected Map<Integer, DatacenterCharacteristics> getDatacenterCharacteristicsList() {
        return datacenterCharacteristicsList;
    }

    /**
     * Sets the datacenter characteristics list.
     *
     * @param datacenterCharacteristicsList the datacenter characteristics list
     */
    protected final void setDatacenterCharacteristicsList(
            Map<Integer, DatacenterCharacteristics> datacenterCharacteristicsList) {
        this.datacenterCharacteristicsList = datacenterCharacteristicsList;
    }

    /**
     * Gets the datacenter requested ids list.
     *
     * @return the datacenter requested ids list
     */
    protected List<Integer> getDatacenterRequestedIdsList() {
        return datacenterRequestedIdsList;
    }

    /**
     * Sets the datacenter requested ids list.
     *
     * @param datacenterRequestedIdsList the new datacenter requested ids list
     */
    protected final void setDatacenterRequestedIdsList(List<Integer> datacenterRequestedIdsList) {
        this.datacenterRequestedIdsList = datacenterRequestedIdsList;
    }

}
