package com.aerosense.aerosense.cycle;

public interface MeasurementAggregate {

  String getFeatureName();

  String getUnit();

  long getSampleCount();

  double getAverage();

  double getMinimum();

  double getMaximum();
}
