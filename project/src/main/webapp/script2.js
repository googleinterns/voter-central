function listPollingLocations() {
  const userAddress = document.getElementById("polling-location-input").elements[0].value;
  fetch(`/pollingLocation?address=${encodeURIComponent(userAddress).replace(/ /g, "%20")}`).then(response => response.json()).then((pollingAddress) => {
    const pollingAddressElement = document.getElementById('poll-address');
    for (fields in pollingAddress.address){
      pollingAddressElement.innerHTML += pollingAddress.address[fields] + "<br>";
    }
  });
}
