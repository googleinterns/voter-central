//Copyright 2019 Google LLC
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

window.onload = function() {
  addCommonElements();
};

/**
 * Adds a brief version of the official election/candidate information to the
 * homepage.
 * Queries the backend database with the user input of address.
 *
 * Each election has the following HTML DOM structure:
 *   <div id="election-i">
 *     <h3>Election name</h3>
 *     <time>Election date</time>
 *     <div id="election-1-positions">
 *       <ul>
 *         <li>
 *           <h4>Position 1</h4>
 *           <p>Table of Candidates</p>
 *           <table class="candidates-table">
 *             <tr>
 *               <td><a href="URL">Candidate Name</a></th>
 *               <td>Party Affiliation</th>
 *               <td>Incumbent?</th>
 *             </tr>
 *           </table>
 *         </li>
 *         <li>
 *           <h4>Position 2...</h4>
 *         </li>
 *       </ul>
 *     </div>
 *     <hr>
 *   </div>
 */
async function addBriefElectionCandidateInformation() {
  // Extract user input of address.
  const address = location.search.substring(location.search.indexOf('=') + 1);

  // Send GET request to /data with address.
  const response = await fetch(`/data?address=${address}`);
  const dataPackage = await response.json();
  const elections = dataPackage.electionsData;

  // Add (brief version) official election/candidate information to HTML.
  const electionsContainer = document.getElementById('elections-container');
  electionsContainer.innerHTML = '';
  for (let i = 0; i < elections.length; i++) {
    const election = elections[i];
    const electionElement = document.createElement('div');
    electionElement.id = `election-${i + 1}`;
    electionElement.innerHTML =
        `<h3>${election.name}</h3>
         <time>${election.date}</time>`;
    // Each election corresponds to a list of positions.
    const positions = election.candidatePositions;
    const positionsElement = document.createElement('div');
    positionsElement.id = electionElement.id + `-positions`;
    const positionsList = document.createElement('ul');
    for (let positionIndex = 0; positionIndex < positions.length;
        positionIndex++) {
      const position = positions[positionIndex];
      const positionItem = document.createElement('li');
      positionItem.innerHTML =
          `<h4>Position ${positionIndex + 1}: ${position.name}</h4>
           <p>Table of Candidates</p>`;
      // Each position corresponds to a table of candidates.
      const candidateIds = position.candidateIds;
      const candidateNames = position.candidateNames;
      const candidatePartyAffiliation = position.candidatePartyAffiliation;
      const candidateIncumbency = position.candidateIncumbency;
      const candidatesTable = document.createElement('table');
      candidatesTable.innerHTML =
          `<tr>
             <th>Candidate Name</th>
             <th>Party Affiliation</th>
             <th>Incumbent?</th>
           </tr>`;
      for (let candidateIndex = 0; candidateIndex < candidateIds.length;
          candidateIndex++) {
        candidatesTable.innerHTML +=
            `<tr>
               <td><a href="candidate.html?candidateId=${candidateIds[candidateIndex]}">
                   ${candidateNames[candidateIndex]}</a></td>
               <td>${candidatePartyAffiliation[candidateIndex]}</td>
               <td>${candidateIncumbency[candidateIndex]}</td>
             </tr>`;
      }
      positionItem.appendChild(candidatesTable);
      positionsList.appendChild(positionItem);
    }
    positionsElement.appendChild(positionsList);
    electionElement.appendChild(positionsElement);
    electionsContainer.appendChild(electionElement);
  }
}
