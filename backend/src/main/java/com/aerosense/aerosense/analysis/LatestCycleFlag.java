package com.aerosense.aerosense.analysis;

import java.util.UUID;

public interface LatestCycleFlag {

  UUID getCycleId();

  boolean getIsFlagged();
}
