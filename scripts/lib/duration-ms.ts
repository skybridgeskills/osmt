export function durationMs(duration: {
  days?: number;
  hours?: number;
  minutes?: number;
  seconds?: number;
  milliseconds?: number;
}): number {
  const { days = 0, hours = 0, minutes = 0, seconds = 0, milliseconds = 0 } = duration;

  return (
    days * 24 * 60 * 60 * 1000 +
    hours * 60 * 60 * 1000 +
    minutes * 60 * 1000 +
    seconds * 1000 +
    milliseconds
  );
}

