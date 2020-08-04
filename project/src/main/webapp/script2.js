function listPollingLocations() {
  const userAddress = document.getElementById("location-input").element[0].value;
  fetch(`/pollingLocation?address=${userAddress}`).then(response => response.json()).then((pollingAddress) => {
    const pollingAddressElement = document.getElementById('poll-address');
    pollingAddressElement.appendChild(pollingAddress);
  });
}
