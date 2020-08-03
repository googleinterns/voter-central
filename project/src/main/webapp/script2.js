function listPollingLocations() {
  fetch('/pollingLocation').then(response => response.json()).then((address) => {
    const pollAddress = document.getElementById('poll-adrress');
    pollAddress.appendChild(address);
  });
}