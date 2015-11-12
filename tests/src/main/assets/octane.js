var results = [];

function AddError(name, error) {
  results.push(name + ": " + error.message);
}
function AddResult(name, result) {
  results.push(name + ": " + result);
}
function AddScore(score) {
  results.push("Score: " + score);
}

BenchmarkSuite.RunSuites({
  NotifyError: AddError,
  NotifyResult: AddResult,
  NotifyScore: AddScore
});

function getResults() {
  return results.join("\n");
}
