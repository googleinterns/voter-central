function listPollingLocations() {
  var address = document.getElementById("location-input").element[0].value;

  fetch(`/pollingLocation?address=${address}`).then(response => response.json()).then((address) => {
    const pollAddress = document.getElementById('poll-address');
    pollAddress.appendChild(address);
  });
}
