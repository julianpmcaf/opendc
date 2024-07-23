package org.opendc.compute.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;


public class SchedulerUtils {
    private SchedulingAlgorithms SchedulingAlgorithmType;

    public enum SchedulingAlgorithms{
        MinMin,
        MaxMix,
        Priority,
        EarliestDeadlineFirst,
    }

    public SchedulerUtils(SchedulingAlgorithms SchedulingAlgorithmType){
        this.SchedulingAlgorithmType = SchedulingAlgorithmType;
    }

    /**
     * Sort list from biggest to smallest, meant for Max Min Scheduling and Priority Scheduling.
     */
    public void MaxMinSortList(List<ComputeService.SchedulingRequest> list){
        Collections.sort(list, new Comparator<ComputeService.SchedulingRequest>() {
            @Override
            public int compare(ComputeService.SchedulingRequest o1, ComputeService.SchedulingRequest o2) {
                return Double.compare(o2.urgency, o1.urgency);
            }
        });
    }

    /**
     * Sort list from biggest to smallest, meant for
     * Min Min Scheduling and
     * Earlist Deadline First Scheduling.
     */
    public void MinMinSortList(List<ComputeService.SchedulingRequest> list){
        Collections.sort(list, Comparator.comparingDouble(e -> e.urgency));
//        return list;
    }

    /**
     * Assign a priority to tasks, meant for Priority Scheduling,
     * Min Min Sheduling, Max Min Scheduling, and
     * Earliest Deadline First Scheduling.
     */
    public void AssignPriority(ComputeService.SchedulingRequest request){
        final double memMultiplier = 0.4;
        final double cpuMultiplier = 0.6;

        //TODO: EDIT MULTIPLIER
        request.urgency = (memMultiplier * request.server.getFlavor().getMemorySize()) +
            (cpuMultiplier * request.server.getFlavor().getCoreCount());
    }

    /**
     * Assign a priority to tasks, meant for
     * Min Min Sheduling and Max Min Scheduling
     */
    public void AssignTaskSize(ComputeService.SchedulingRequest request){
        final double memMultiplier = 1;
        final double cpuMultiplier = 1;

        //TODO: EDIT MULTIPLIER
        request.urgency = (memMultiplier * request.server.getFlavor().getMemorySize()) +
            (cpuMultiplier * request.server.getFlavor().getCoreCount());

    }

    /**
     * Assign a priority to tasks, meant for
     * Earliest Deadline First Scheduling
     */

    public void AssignDeadline(ComputeService.SchedulingRequest request, List<ComputeService.SchedulingRequest> list){
        final double startOffset = 1;
        final double memMultiplier = 1;
        final double cpuMultiplier = 1;

        request.urgency = (memMultiplier * request.server.getFlavor().getMemorySize()) +
            (cpuMultiplier * request.server.getFlavor().getCoreCount()) + (startOffset + (list.size() + 1));
    }

    public void performScheduling(ComputeService.SchedulingRequest request, List<ComputeService.SchedulingRequest> list){
        switch (SchedulingAlgorithmType){
            case MinMin:
            {
                AssignTaskSize(request);
                list.add(request);
                MinMinSortList(list);
                break;
            }
            case MaxMix:
            {
                AssignTaskSize(request);
                list.add(request);
                MaxMinSortList(list);
                break;
            }
            case Priority:
            {
                AssignPriority(request);
                list.add(request);
                MinMinSortList(list);
                break;
            }
            case EarliestDeadlineFirst:
            {
                AssignDeadline(request,list);
                list.add(request);
                MinMinSortList(list);
                break;
            }
        }
    }



}
