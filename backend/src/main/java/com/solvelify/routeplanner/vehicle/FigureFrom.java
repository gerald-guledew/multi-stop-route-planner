package com.solvelify.routeplanner.vehicle;

/** Where a vehicle's fuel use comes from. */
public enum FigureFrom {

    /** What its dashboard shows: the car's own average, with the hills and town it usually drives. */
    DASHBOARD,

    /** NZTA's register: the laboratory figure most cars like it carry. Real driving uses more. */
    REGISTER
}
