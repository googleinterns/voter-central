// Copyright 2019 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

// use modern JavaScript (ES5)
"use strict"

// Returns Address for Polling location from polling location servlet.
function listPollingLocations() {
  const userAddress = document.getElementById("polling-location-input").elements[0].value;
  fetch(`/pollingLocation?address=${encodeURIComponent(userAddress).replace(/ /g, "%20")}`)
      .then(response => response.json()).then((pollingAddress) => {
    const pollingAddressElement = document.getElementById('poll-address');
    for (const fields in pollingAddress.address){
      pollingAddressElement.innerHTML += pollingAddress.address[fields] + "<br>";
    }
  }).catch(() => {
    document.getElementById('poll-address').innerText = 
        "Polling location not found. Please verify that you have entered a valid address.";
  });
}
