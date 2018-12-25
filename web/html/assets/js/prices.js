/*
  There's not much here except for some poorly written JS functions. And since you're 
  already here, it can't hurt to take a look at http://youmightnotneedjquery.com/
*/

class ItemRow {
  constructor (item) {
    this.item = item;
    this.row = "<tr value={{id}}>{{data}}</tr>";
    this.fields = {};
    
    // Build HTML elements
    var rowBuilder = [];
    rowBuilder.push(
      this.buildNameField(),
      this.buildGemFields(),
      this.buildBaseFields(),
      this.buildMapFields(),
      this.buildPriceFields(),
      this.buildChangeField(),
      this.buildDailyField(),
      this.buildTotalField()
    );

    this.row = this.row
      .replace("{{id}}", item.id)
      .replace("{{data}}", rowBuilder.join(""));
  }

  buildNameField() {
    let template = `
    <td>
      <div class='d-flex align-items-center'>
        <span class='img-container img-container-sm text-center mr-1'><img src="{{icon}}"></span>
        <span class='cursor-pointer {{color}}'>{{name}}{{type}}</span>{{var}}{{link}}
      </div>
    </td>
    `.trim();
  
    template = template.replace("{{url}}", "https://poe.watch/item?league=" + FILTER.league.name + "&id=" + this.item.id);
  
    if (FILTER.category === "base") {
      if (this.item.var === "shaper") {
        this.item.icon += "&shaper=1";
        template = template.replace("{{color}}", "item-shaper");
      } else if (this.item.var === "elder") {
        this.item.icon += "&elder=1";
        template = template.replace("{{color}}", "item-elder");
      }
    }

    // Use TLS for icons for that sweet, sweet secure site badge
    this.item.icon = this.item.icon.replace("http://", "https://");
    template = template.replace("{{icon}}", this.item.icon);

    if (this.item.frame === 9) {
      template = template.replace("{{color}}", "item-foil");
    } else {
      template = template.replace("{{color}}", "");
    }
  
    if (FILTER.category === "enchantment") {
      if (this.item.var !== null) {
        let splitVar = this.item.var.split('-');
        for (var num in splitVar) {
          this.item.name = this.item.name.replace("#", splitVar[num]);
        }
      }
    }
    
    template = template.replace("{{name}}", this.item.name);
  
    if (this.item.type) {
      let tmp = "<span class='subtext-1'>, " + this.item.type + "</span>";;
      template = template.replace("{{type}}", tmp);
    } else {
      template = template.replace("{{type}}", "");
    }
  
    if (this.item.links) {
      let tmp = " <span class='badge custom-badge-gray ml-1'>" + this.item.links + " link</span>";
      template = template.replace("{{link}}", tmp);
    } else {
      template = template.replace("{{link}}", "");
    }
  
    if (this.item.var && FILTER.category !== "enchantment") {
      let tmp = " <span class='badge custom-badge-gray ml-1'>" + this.item.var + "</span>";
      template = template.replace("{{var}}", tmp);
    } else {
      template = template.replace("{{var}}", "");
    }
  
    return template;
  }
  
  buildGemFields() {
    // Don't run if item is not a gem
    if (this.item.frame !== 4) return "";
  
    let template = `
    <td><span class='badge custom-badge-block custom-badge-gray'>{{lvl}}</span></td>
    <td><span class='badge custom-badge-block custom-badge-gray'>{{quality}}</span></td>
    <td><span class='badge custom-badge-{{color}}'>{{corr}}</span></td>
    `.trim();
  
    template = template.replace("{{lvl}}",      this.item.lvl);
    template = template.replace("{{quality}}",  this.item.quality);
    
    if (this.item.corrupted) {
      template = template.replace("{{color}}",  "red");
      template = template.replace("{{corr}}",   "✓");
    } else {
      template = template.replace("{{color}}",  "green");
      template = template.replace("{{corr}}",   "✕");
    }
  
    return template;
  }
  
  buildBaseFields() {
    // Don't run if item is not a gem
    if (FILTER.category !== "base") return "";
    return "<td class='nowrap'><span class='badge custom-badge-block custom-badge-gray'>" + this.item.ilvl + "</span></td>";
  }
  
  buildMapFields() {
    // Don't run if item is not a map
    if (FILTER.category !== "map") {
      return "";
    }

    let template = `
    <td class='nowrap'>
      <span class='badge custom-badge-block custom-badge-gray'>{{tier}}</span>
    </td>
    `.trim();

    return this.item.tier ? template.replace("{{tier}}", this.item.tier) : "<td></td>";
  }
  
  buildPriceFields() {
    let template = `
    <td>
      <div class='pricebox'>{{sparkline}}{{chaos_icon}}{{chaos_price}}</div>
    </td>
    <td>
      <div class='pricebox'>{{ex_icon}}{{ex_price}}</div>
    </td>
    `.trim();

    let chaosContainer  = TEMPLATE_imgContainer.trim().replace("{{img}}", ICON_CHAOS);
    let exContainer     = TEMPLATE_imgContainer.trim().replace("{{img}}", ICON_EXALTED);
    let sparkLine       = this.buildSparkLine(this.item);
  
    template = template.replace("{{sparkline}}",    sparkLine);
    template = template.replace("{{chaos_price}}",  ItemRow.roundPrice(this.item.mean));
    template = template.replace("{{chaos_icon}}",   chaosContainer);
  
    if (this.item.exalted >= 1) {
      template = template.replace("{{ex_icon}}",    exContainer);
      template = template.replace("{{ex_price}}",   ItemRow.roundPrice(this.item.exalted));
    } else {
      template = template.replace("{{ex_icon}}",    "");
      template = template.replace("{{ex_price}}",   "");
    }
    
    return template;
  }
  
  buildSparkLine() {
    if (!this.item.spark) return "";
  
    let svgColorClass = this.item.change > 0 ? "sparkline-green" : "sparkline-orange";
    let svg = document.createElement("svg");
    
    svg.setAttribute("class", "sparkline " + svgColorClass);
    svg.setAttribute("width", 60);
    svg.setAttribute("height", 30);
    svg.setAttribute("stroke-width", 3);
  
    sparkline(svg, this.item.spark);
    
    return svg.outerHTML;
  }
  
  buildChangeField() {
    let template = `
    <td>
      <span class='badge custom-badge-block custom-badge-{{color}}'>
        {{percent}}%
      </span>
    </td>
    `.trim();
  
    let change = 0;
  
    if (this.item.change > 999) {
      change = 999;
    } else if (this.item.change < -999) {
      change = -999;
    } else {
      change = Math.round(this.item.change); 
    }

    if (change >= 100) {
      template = template.replace("{{color}}", "green-ex");
    } else if (change <= -100) {
      template = template.replace("{{color}}", "red-ex");
    } else if (change >= 30) {
      template = template.replace("{{color}}", "green");
    } else if (change <= -30) {
      template = template.replace("{{color}}", "red");
    } else if (change >= 15) {
      template = template.replace("{{color}}", "green-lo");
    } else if (change <= -15) {
      template = template.replace("{{color}}", "red-lo");
    } else {
      template = template.replace("{{color}}", "gray");
    }

    return template.replace("{{percent}}", change);
  }
  
  buildDailyField() {
    let template = `
    <td>
      <span class='badge custom-badge-block custom-badge-{{color}}'>
        {{daily}}
      </span>
    </td>
    `.trim();

    if (FILTER.league.active) {
      if (this.item.daily >= 20) {
        template = template.replace("{{color}}", "gray");
      } else if (this.item.daily >= 10) {
        template = template.replace("{{color}}", "orange-lo");
      } else if (this.item.daily >= 5) {
        template = template.replace("{{color}}", "red-lo");
      } else if (this.item.daily >= 0) {
        template = template.replace("{{color}}", "red");
      }
    } else {
      template = template.replace("{{color}}", "gray");
    }
  
    return template.replace("{{daily}}", this.item.daily);
  }

  buildTotalField() {
    let template = `
    <td>
      <span class='badge custom-badge-block custom-badge-gray'>
        {{total}}
      </span>
    </td>
    `.trim();
  
    return template.replace("{{total}}", this.item.total);
  }

  static roundPrice(price) {
    const numberWithCommas = (x) => {
      var parts = x.toString().split(".");
      parts[0] = parts[0].replace(/\B(?=(\d{3})+(?!\d))/g, ",");
      return parts.join(".");
    }
  
    return numberWithCommas(Math.round(price * 100) / 100);
  }
}

class DetailsModal {
  constructor() {
    this.dataSets = {};

    // Find modal in DOM
    this.modal = $("#modal-details");

    this.id = null;
    this.league = null;
    this.chart = null;
    this.dataset = 1;

    this.nameHtml = null;
    this.icon = null;

    let chartGradientPlugin = {
      beforeDatasetUpdate: function(chart) {
        if (!chart.width) return;
  
        // Create the linear gradient  chart.scales['x-axis-0'].width
        var gradient = chart.ctx.createLinearGradient(0, 0, 0, 250);
  
        gradient.addColorStop(0.0, 'rgba(247, 233, 152, 1)');
        gradient.addColorStop(1.0, 'rgba(244, 149, 179, 1)');
  
        // Assign the gradient to the dataset's border color.
        chart.data.datasets[0].borderColor = gradient;
      }
    };

    let chartDataPlugin = {
      beforeUpdate: function(chart) {
        // Don't run if data has not yet been initialized
        if (chart.data.data.length < 1) return;
  
        var keys = chart.data.data.keys;
        var vals = chart.data.data.vals;
  
        chart.data.labels = keys;
  
        switch (DETMODAL.dataset) {
          case 1: chart.data.datasets[0].data = vals.mean;   break;
          case 2: chart.data.datasets[0].data = vals.median; break;
          case 3: chart.data.datasets[0].data = vals.mode;   break;
          case 4: chart.data.datasets[0].data = vals.daily;  break;
        }
      }
    };
  
    this.chart_settings = {
      plugins: [chartDataPlugin, chartGradientPlugin],
      type: "line",
      data: {
        data: [],
        labels: [],
        datasets: [{
          data: [],
          backgroundColor: "rgba(0, 0, 0, 0.2)",
          borderColor: "rgba(255, 255, 255, 0.5)",
          borderWidth: 3,
          lineTension: 0.4,
          pointRadius: 0
        }]
      },
      options: {
        title: {display: false},
        layout: {padding: 0},
        legend: {display: false},
        responsive: true,
        maintainAspectRatio: false,
        animation: {duration: 0},
        hover: {animationDuration: 0},
        responsiveAnimationDuration: 0,
        tooltips: {
          intersect: false,
          mode: "index",
          callbacks: {
            title: function(tooltipItem, data) {
              let price = data['datasets'][0]['data'][tooltipItem[0]['index']];
              return price ? price : "No data";
            },
            label: function(tooltipItem, data) {
              return data['labels'][tooltipItem['index']];
            }
          },
          backgroundColor: '#fff',
          titleFontSize: 16,
          titleFontColor: '#222',
          bodyFontColor: '#444',
          bodyFontSize: 14,
          displayColors: false,
          borderWidth: 1,
          borderColor: '#aaa'
        },
        scales: {
          yAxes: [{
            ticks: {
              beginAtZero: true,
              padding: 0
            }
          }],
          xAxes: [{
            ticks: {
              callback: function(value, index, values) {
                return (value ? value : '');
              },
              maxRotation: 0,
              padding: 0
            }
          }]
        }
      }
    }

    // Instantiate chart
    let chartCanvas = $("#modal-chart", this.modal);
    this.chart = new Chart(chartCanvas, this.chart_settings);

    // Create league select event listener
    $("#modal-leagues", this.modal).change(function(){
      DETMODAL.league = $(":selected", this).val();
      DETMODAL.updateContent();
    });
  
    // Create dataset radio event listener
    $("#modal-radio", this.modal).change(function(){
      DETMODAL.dataset = parseInt($("input[name=dataset]:checked", this).val());
      DETMODAL.updateContent();
    });
  }

  onRowClick(event) {
    let target = $(event.currentTarget);
    let id = parseInt(target.attr('value'));
  
    // If user clicked on a table that does not contain an id
    if (isNaN(id)) {
      return;
    }

    console.log("Clicked on row id: " + id);

    // Show buffer and hide content
    this.setBufferVisibility(true);

    // Define current row as parent target row
    this.league = FILTER.league.name;
    this.dataset = 1;
    this.id = id;
  
    // Load history data
    if (id in this.dataSets) {
      console.log("History source: local");
      this.updateContent();
    } else {
      console.log("History source: remote");

      let request = $.ajax({
        url: "https://api.poe.watch/item",
        data: {id: this.id},
        type: "GET",
        async: true,
        dataTypes: "json"
      });
    
      request.done(DetailsModal.requestDone);
    }

    let item = this.findItem(id);
    $("#modal-icon", this.modal).attr("src", item.icon);
    $("#modal-name", this.modal).html(this.buildNameField(item));
    
    // Show modal
    this.modal.modal("show");
  }

  static requestDone(payload) {
    DETMODAL.dataSets[DETMODAL.id] = payload;
    DETMODAL.updateContent();
  }

  updateContent() {
    // Clear previous leagues from selector
    $("#modal-leagues", this.modal).find('option').remove();

    // Get current item user clicked on
    let item = this.dataSets[this.id];

    // Get list of leagues with history data
    let leagues = this.getLeagues(item);

    // Add leagues as selector options
    this.createLeagueSelector(leagues);

    // Format league data
    let leaguePayload = this.getPayload();
    this.chart.data.data = this.formatHistory(leaguePayload);
    this.chart.update();

    // Update modal table
    $("#modal-mean",     this.modal).html( formatNum(leaguePayload.mean)   );
    $("#modal-median",   this.modal).html( formatNum(leaguePayload.median) );
    $("#modal-mode",     this.modal).html( formatNum(leaguePayload.mode)   );
    $("#modal-total",    this.modal).html( formatNum(leaguePayload.total)  );
    $("#modal-daily",    this.modal).html( formatNum(leaguePayload.daily)  );
    $("#modal-exalted",  this.modal).html( formatNum(leaguePayload.exalted));

    // Hide buffer and show content
    this.setBufferVisibility(false);
  }

  findItem(id) {
    for (let i = 0; i < ITEMS.length; i++) {
      if (ITEMS[i].id === id) {
         return ITEMS[i];
      }
    }

    return null;
  }

  setBufferVisibility(visible) { 
    if (visible) {
      $("#modal-body-buffer", this.modal).removeClass("d-none").addClass("d-flex");
      $("#modal-body-content", this.modal).addClass("d-none").removeClass("d-flex");
    } else {
      $("#modal-body-buffer", this.modal).addClass("d-none").removeClass("d-flex");
      $("#modal-body-content", this.modal).removeClass("d-none").addClass("d-flex");
    }
  }

  buildNameField(item) {
    // Fix name if item is enchantment
    if (item.category === "enchantment" && item.variation !== null) {
      let splitVar = item.variation.split('-');
  
      for (var num in splitVar) {
        item.name = item.name.replace("#", splitVar[num]);
      }
    }
  
    // Begin builder
    let builder = item.name;
  
    if (item.type) {
      builder += "<span class='subtext-1'>, " + item.type + "</span>";;
    }
  
    if (item.frame === 9) {
      builder = "<span class='item-foil'>" + builder + "</span>";
    } else if (item.variation === "shaper") {
      builder = "<span class='item-shaper'>" + builder + "</span>";
    } else if (item.variation === "elder") {
      builder = "<span class='item-elder'>" + builder + "</span>";
    }
  
    if (item.variation && item.category !== "enchantment") { 
      builder += " <span class='badge custom-badge-gray ml-1'>" + item.variation + "</span>";
    } 
    
    if (item.tier) {
      builder += " <span class='badge custom-badge-gray ml-1'>Tier " + item.tier + "</span>";
    } 
  
    if (item.ilvl) {
      builder += " <span class='badge custom-badge-gray ml-1'>iLvl " + item.ilvl + "</span>";
    } 
    
    if (item.links) {
      builder += " <span class='badge custom-badge-gray ml-1'>" + item.links + " Link</span>";
    }
  
    if (item.frame === 4) {
      builder += "<span class='badge custom-badge-gray ml-1'>Lvl " + item.lvl + "</span>";
      builder += "<span class='badge custom-badge-gray ml-1'>Quality " + item.quality + "</span>";
  
      if (item.corrupted) {
        builder += "<span class='badge custom-badge-red ml-1'>Corrupted</span>";
      }
    }
  
    return builder;
  }

  formatIcon(item) {
    var icon = item.icon.replace("http://", "https://");
  
    if (item.variation === "shaper") {
      icon += "&shaper=1";
    } else if (item.variation === "elder") {
      icon += "&elder=1";
    }
  
    // Flaks have no params
    if (!icon.includes("?")) {
      return icon;
    }
  
    let splitIcon = icon.split("?");
    let splitParams = splitIcon[1].split("&");
    let newParams = "";
  
    for (let i = 0; i < splitParams.length; i++) {
      switch (splitParams[i].split("=")[0]) {
        case "scale": 
          break;
        default:
          newParams += "&" + splitParams[i];
          break;
      }
    }
  
    if (newParams) {
      icon = splitIcon[0] + "?" + newParams.substr(1);
    } else {
      icon = splitIcon[0];
    }

    return icon;
  }


  createLeagueSelector(leagues) {
    let builder = "";
  
    for (let i = 0; i < leagues.length; i++) {
      let display = leagues[i].active ? leagues[i].display : "● " + leagues[i].display;
      let selected = FILTER.league.name === leagues[i].name ? "selected" : "";
  
      builder += "<option value='{{value}}' {{selected}}>{{name}}</option>"
        .replace("{{selected}}", selected)
        .replace("{{value}}", leagues[i].name)
        .replace("{{name}}", display);
    }
  
    $("#modal-leagues", this.modal).html(builder);
  }

  getPayload() {
    for (let i = 0; i < this.dataSets[this.id].data.length; i++) {
      if (this.dataSets[this.id].data[i].league.name === this.league) {
        return this.dataSets[this.id].data[i];
      }
    }
  
    return null;
  }

  getLeagues(item) {
    let leagues = [];

    for (let i = 0; i < item.data.length; i++) {
      leagues.push({
        name: item.data[i].league.name,
        display: item.data[i].league.display,
        active: item.data[i].league.active
      });
    }
  
    return leagues;
  }

  formatHistory(leaguePayload) {
    let keys = [];
    let vals = {
      mean:   [],
      median: [],
      mode:   [],
      daily:  []
    };
  
    const msInDay = 86400000;
    let firstDate = null, lastDate = null;
    let totalDays = null, elapDays = null;
    let startDate = null, endDate  = null;
    let daysMissingStart = 0, daysMissingEnd = 0;
    let startEmptyPadding = 0;
  
    // If there are any history entries for this league, find the first and last date
    if (leaguePayload.history.length) {
      firstDate = new Date(leaguePayload.history[0].time);
      lastDate = new Date(leaguePayload.history[leaguePayload.history.length - 1].time);
    }
  
    // League should always have a start date
    if (leaguePayload.league.start) {
      startDate = new Date(leaguePayload.league.start);
    }
  
    // Permanent leagues don't have an end date
    if (leaguePayload.league.end) {
      endDate = new Date(leaguePayload.league.end);
    }
  
    // Find duration for non-permanent leagues
    if (startDate && endDate) {
      let diff = Math.abs(endDate.getTime() - startDate.getTime());
      totalDays = Math.floor(diff / msInDay);
      
      if (leaguePayload.league.active) {
        let diff = Math.abs(new Date().getTime() - startDate.getTime());
        elapDays = Math.floor(diff / msInDay);
      } else {
        elapDays = totalDays;
      }
    }
  
    // Find how many days worth of data is missing from the league start
    if (leaguePayload.league.id > 2) {
      if (firstDate && startDate) {
        let diff = Math.abs(firstDate.getTime() - startDate.getTime());
        daysMissingStart = Math.floor(diff / msInDay);
      }
    } 
  
    // Find how many days worth of data is missing from the league end, if league has ended
    if (leaguePayload.league.active) {
      // League is active, compare time of last entry to right now
      if (lastDate) {
        let diff = Math.abs(new Date().getTime() - lastDate.getTime());
        daysMissingEnd = Math.floor(diff / msInDay);
      }
    } else {
      // League has ended, compare time of last entry to time of league end
      if (lastDate && endDate) {
        let diff = Math.abs(lastDate.getTime() - endDate.getTime());
        daysMissingEnd = Math.floor(diff / msInDay);
      }
    }
  
    // Find number of ticks the graph should be padded with empty entries on the left
    if (leaguePayload.league.id > 2) {
      if (totalDays !== null && elapDays !== null) {
        startEmptyPadding = totalDays - elapDays;
      }
    } else {
      startEmptyPadding = 120 - leaguePayload.history.length;
    }
  
  
    // Right, now that we have all the dates, durations and counts we can start 
    // building the actual payload
  
  
    // Bloat using 'null's the amount of days that should not have a tooltip
    for (let i = 0; i < startEmptyPadding; i++) {
      vals.mean.push(null);
      vals.median.push(null);
      vals.mode.push(null);
      vals.daily.push(null);
      keys.push(null);
    }
  
    // If entries are missing before the first entry, fill with "No data"
    if (daysMissingStart) {
      let date = new Date(startDate);
  
      for (let i = 0; i < daysMissingStart; i++) {
        vals.mean.push(0);
        vals.median.push(0);
        vals.mode.push(0);
        vals.daily.push(0);
        keys.push(this.formatDate(date.addDays(i)));
      }
    }
  
    // Add actual history data
    for (let i = 0; i < leaguePayload.history.length; i++) {
      const entry = leaguePayload.history[i];
  
      // Add current entry values
      vals.mean.push(Math.round(entry.mean * 100) / 100);
      vals.median.push(Math.round(entry.median * 100) / 100);
      vals.mode.push(Math.round(entry.mode * 100) / 100);
      vals.daily.push(entry.daily);
      keys.push(this.formatDate(entry.time));
  
      // Check if there are any missing entries between the current one and the next one
      if (i + 1 < leaguePayload.history.length) {
        const nextEntry = leaguePayload.history[i + 1];
  
        // Get dates
        let currentDate = new Date(entry.time);
        let nextDate = new Date(nextEntry.time);
  
        // Get difference in days between entries
        let timeDiff = Math.abs(nextDate.getTime() - currentDate.getTime());
        let diffDays = Math.floor(timeDiff / (1000 * 3600 * 24)) - 1; 
  
        // Fill missing days with "No data" (if any)
        for (let i = 0; i < diffDays; i++) {
          vals.mean.push(0);
          vals.median.push(0);
          vals.mode.push(0);
          vals.daily.push(0);
          keys.push(this.formatDate(currentDate.addDays(i + 1)));
        }
      }
    }
  
    // If entries are missing after the first entry, fill with "No data"
    if (daysMissingEnd && lastDate) {
      let date = new Date(lastDate);
      date.setDate(date.getDate() + 1);
  
      for (let i = 0; i < daysMissingEnd; i++) {
        vals.mean.push(0);
        vals.median.push(0);
        vals.mode.push(0);
        vals.daily.push(0);
        keys.push(this.formatDate(date.addDays(i)));
      }
    }
  
    // Add current values
    if (leaguePayload.league.active) {
      vals.mean.push(Math.round(leaguePayload.mean * 100) / 100);
      vals.median.push(Math.round(leaguePayload.median * 100) / 100);
      vals.mode.push(Math.round(leaguePayload.mode * 100) / 100);
      vals.daily.push(leaguePayload.daily);
      keys.push("Now");
    }
  
    // Return generated data
    return {
      'keys': keys,
      'vals': vals
    }
  }

  formatDate(date) {
    const MONTH_NAMES = [
      "Jan", "Feb", "Mar", "Apr", "May", "Jun", 
      "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
    ];
  
    let s = new Date(date);

    return s.getDate() + " " + MONTH_NAMES[s.getMonth()];
  }
}

// Default item search filter options
var FILTER = {
  league: SERVICE_leagues[0],
  category: null,
  group: "all",
  showLowConfidence: false,
  links: null,
  rarity: null,
  tier: null,
  search: null,
  gemLvl: null,
  gemQuality: null,
  gemCorrupted: null,
  ilvl: null,
  influence: null,
  parseAmount: 150,
  sortFunction: null
};

var ITEMS = [];
var LEAGUES = null;
var INTERVAL;
var DETMODAL = new DetailsModal();

// Re-used icon urls
const ICON_ENCHANTMENT = "https://web.poecdn.com/image/Art/2DItems/Currency/Enchantment.png?scale=1&w=1&h=1";
const ICON_EXALTED = "https://web.poecdn.com/image/Art/2DItems/Currency/CurrencyAddModToRare.png?scale=1&w=1&h=1";
const ICON_CHAOS = "https://web.poecdn.com/image/Art/2DItems/Currency/CurrencyRerollRare.png?scale=1&w=1&h=1";

var TEMPLATE_imgContainer = "<span class='img-container img-container-sm text-center mr-1'><img src={{img}}></span>";

$(document).ready(function() {
  parseQueryParams();

  makeGetRequest();
  defineListeners();
}); 

//------------------------------------------------------------------------------------------------------------
// Data prep
//------------------------------------------------------------------------------------------------------------

function parseQueryParams() {
  let tmp;

  if (tmp = parseQueryParam('league')) {
    tmp = getServiceLeague(tmp);

    if (tmp) {
      FILTER.league = tmp;
      $("#search-league").val(FILTER.league.name);
    }
  }

  updateQueryParam("league", FILTER.league.name);

  if (tmp = parseQueryParam('category')) {
    FILTER.category = tmp;
  } else {
    FILTER.category = "currency";
    updateQueryParam("category", FILTER.category);
  }

  if (tmp = parseQueryParam('group')) {
    FILTER.group = tmp;
    $('#search-group').val(tmp);
  }

  if (tmp = parseQueryParam('search')) {
    FILTER.search = tmp;
  }

  if (tmp = parseQueryParam('confidence')) {
    FILTER.showLowConfidence = true;
  }

  if (tmp = parseQueryParam('rarity')) {
    if      (tmp === "unique") FILTER.rarity =    3;
    else if (tmp ===  "relic") FILTER.rarity =    9;
  }

  if (tmp = parseQueryParam('links')) {
    if (tmp ===  "all") FILTER.links = -1;
    else FILTER.links = parseInt(tmp);
  }

  if (tmp = parseQueryParam('tier')) {
    $('#select-tier').val(tmp);
    if (tmp === "none") FILTER.tier = 0;
    else FILTER.tier = parseInt(tmp);
  }

  if (tmp = parseQueryParam('corrupted')) {
    FILTER.gemCorrupted = tmp === "true";
  }

  if (tmp = parseQueryParam('lvl')) {
    $('#select-level').val(tmp);
    FILTER.gemLvl = parseInt(tmp);
  }

  if (tmp = parseQueryParam('quality')) {
    $('#select-quality').val(tmp);
    FILTER.gemQuality = parseInt(tmp);
  }

  if (tmp = parseQueryParam('ilvl')) {
    $('#select-ilvl').val(tmp);
    FILTER.ilvl = parseInt(tmp);
  }

  if (tmp = parseQueryParam('influence')) {
    $('#select-influence').val(tmp);
    FILTER.influence = tmp;
  }

  if (tmpCol = parseQueryParam('sortby')) {
    let element;

    // Find column that matches the provided param
    $(".sort-column").each(function( index ) {
      if (this.innerHTML.toLowerCase() === tmpCol) {
        element = this;
        return;
      }
    });

    // If there was no match then clear the browser's query params
    if (!element) {
      updateQueryParam("sortby", null);
      updateQueryParam("sortorder", null);
      return;
    }

    // Get column name
    let col = element.innerHTML.toLowerCase();

    // If there was a sortorder query param as well
    if (tmpOrder = parseQueryParam('sortorder')) {
      let order = null;
      let color;

      // Only two options
      if (tmpOrder === "descending") {
        order = "descending";
        color = "custom-text-green";
      } else if (tmpOrder === "ascending") {
        order = "ascending";
        color = "custom-text-red";
      }

      // If user provided a third option, count that as invalid and
      // clear the browser's query params
      if (!order) {
        updateQueryParam("sortby", null);
        updateQueryParam("sortorder", null);
        return;
      }

      // User-provided params were a-ok, set the sort function and
      // add indication which col is being sorted
      console.log("Sorting: " + col + " " + order);
      FILTER.sortFunction = getSortFunc(col, order);
      $(element).addClass(color);
    }
  }
}

function defineListeners() {
  // League
  $("#search-league").on("change", function(){
    let tmp = getServiceLeague($(":selected", this).val());
    if (!tmp) return;
    
    FILTER.league = tmp;
    console.log("Selected league: " + FILTER.league.name);
    updateQueryParam("league", FILTER.league.name);
    makeGetRequest();
  });

  // Group
  $("#search-group").change(function(){
    FILTER.group = $(this).find(":selected").val();
    console.log("Selected group: " + FILTER.group);
    updateQueryParam("group", FILTER.group);
    sortResults();
  });

  // Load all button
  $("#button-showAll").on("click", function(){
    console.log("Button press: show all");
    $(this).hide();
    FILTER.parseAmount = -1;
    sortResults();
  });

  // Searchbar
  $("#search-searchbar").on("input", function(){
    FILTER.search = $(this).val().toLowerCase().trim();
    console.log("Search: " + FILTER.search);
    updateQueryParam("search", FILTER.search);
    sortResults();
  });

  // Low confidence
  $("#radio-confidence").on("change", function(){
    let option = $("input:checked", this).val() === "true";
    console.log("Show low daily: " + option);
    FILTER.showLowConfidence = option;
    updateQueryParam("confidence", option);
    sortResults();
  });

  // Rarity
  $("#radio-rarity").on("change", function(){
    FILTER.rarity = $(":checked", this).val();
    console.log("Rarity filter: " + FILTER.rarity);
    updateQueryParam("rarity", FILTER.rarity);

    if      (FILTER.rarity ===    "all") FILTER.rarity = null;
    else if (FILTER.rarity === "unique") FILTER.rarity =    3;
    else if (FILTER.rarity ===  "relic") FILTER.rarity =    9;
    
    sortResults();
  });
  
  // Item links
  $("#radio-links").on("change", function(){
    FILTER.links = $(":checked", this).val();
    console.log("Link filter: " + FILTER.links);
    updateQueryParam("links", FILTER.links);
    if      (FILTER.links === "none") FILTER.links = null;
    else if (FILTER.links ===  "all") FILTER.links = -1;
    else FILTER.links = parseInt(FILTER.links);
    sortResults();
  });

  // Map tier
  $("#select-tier").on("change", function(){
    FILTER.tier = $(":selected", this).val();
    console.log("Map tier filter: " + FILTER.tier);
    updateQueryParam("tier", FILTER.tier);
    if (FILTER.tier === "all") FILTER.tier = null;
    else if (FILTER.tier === "none") FILTER.tier = 0;
    else FILTER.tier = parseInt(FILTER.tier);
    sortResults();
  });

  // Gem level
  $("#select-level").on("change", function(){
    FILTER.gemLvl = $(":selected", this).val();
    console.log("Gem lvl filter: " + FILTER.gemLvl);
    if (FILTER.gemLvl === "all") FILTER.gemLvl = null;
    else FILTER.gemLvl = parseInt(FILTER.gemLvl);
    updateQueryParam("lvl", FILTER.gemLvl);
    sortResults();
  });

  // Gem quality
  $("#select-quality").on("change", function(){
    FILTER.gemQuality = $(":selected", this).val();
    console.log("Gem quality filter: " + FILTER.gemQuality);
    if (FILTER.gemQuality === "all") FILTER.gemQuality = null;
    else FILTER.gemQuality = parseInt(FILTER.gemQuality);
    updateQueryParam("quality", FILTER.gemQuality);
    sortResults();
  });

  // Gem corrupted
  $("#radio-corrupted").on("change", function(){
    FILTER.gemCorrupted = $(":checked", this).val();
    console.log("Gem corruption filter: " + FILTER.gemCorrupted);
    if (FILTER.gemCorrupted === "all") FILTER.gemCorrupted = null;
    else FILTER.gemCorrupted = FILTER.gemCorrupted === "true";
    updateQueryParam("corrupted", FILTER.gemCorrupted);
    sortResults();
  });

  // Base iLvl
  $("#select-ilvl").on("change", function(){
    let ilvl = $(":selected", this).val();
    console.log("Base iLvl filter: " + ilvl);
    FILTER.ilvl = ilvl === "all" ? null : parseInt(ilvl);
    updateQueryParam("ilvl", ilvl);
    sortResults();
  });

  // Base influence
  $("#select-influence").on("change", function(){
    FILTER.influence = $(":selected", this).val();
    console.log("Influence filter: " + FILTER.influence);
    if (FILTER.influence == "all") FILTER.influence = null; 
    updateQueryParam("influence", FILTER.influence);
    sortResults();
  });

  // Expand row
  $("#searchResults > tbody").delegate("tr", "click", function(event) {
    DETMODAL.onRowClick(event);
  });

  // Live search toggle
  $("#live-updates").on("change", function(){
    let live = $("input[name=live]:checked", this).val() === "true";
    console.log("Live updates: " + live);

    if (live) {
      $("#progressbar-live").css("animation-name", "progressbar-live").show();
      INTERVAL = setInterval(timedRequestCallback, 60 * 1000);
    } else {
      $("#progressbar-live").css("animation-name", "").hide();
      clearInterval(INTERVAL);
    }
  });

  // Sort
  $(".sort-column").on("click", function(){
    // Get col name
    let col = $(this)[0].innerHTML.toLowerCase();
    // Get order tag, if present
    let order = $(this).attr("order");
    let color = null;

    // Remove all data from all sort columns
    $(".sort-column")
      .attr("class", "sort-column")
      .attr("order", null);

    // Toggle descriptions and orders
    if (!order) {
      order = "descending";
      color = "custom-text-green";
    } else if (order === "descending") {
      order = "ascending";
      color = "custom-text-red";
    } else if (order === "ascending") {
      updateQueryParam("sortby", null);
      updateQueryParam("sortorder", null);
      console.log("Sorting: default");
      FILTER.sortFunction = sort_priceDesc;
      sortResults();
      return;
    }

    updateQueryParam("sortby", col);
    updateQueryParam("sortorder", order);

    // Set clicked col's data
    $(this).attr("order", order);
    $(this).addClass(color);

    console.log("Sorting: " + col + " " + order);
    FILTER.sortFunction = getSortFunc(col, order);

    sortResults();
  });
}

//------------------------------------------------------------------------------------------------------------
// Requests
//------------------------------------------------------------------------------------------------------------

function makeGetRequest() {
  $("#searchResults tbody").empty();
  $("#buffering-main").show();
  $("#button-showAll").hide();
  $(".buffering-msg").remove();

  let request = $.ajax({
    url: "https://api.poe.watch/get.php",
    data: {
      league: FILTER.league.name, 
      category: FILTER.category
    },
    type: "GET",
    async: true,
    dataTypes: "json"
  });

  request.done(function(json) {
    console.log("Got " + json.length + " items from request");
    $("#buffering-main").hide();
    $(".buffering-msg").remove();

    ITEMS = json;
    sortResults();
  });

  request.fail(function(response) {
    ITEMS = [];

    $(".buffering-msg").remove();

    let buffering = $("#buffering-main");
    buffering.hide();

    let msg;
    if (response.status) {
      msg = "<div class='buffering-msg align-self-center mb-2'>" + response.responseJSON.error + "</div>";
    } else {
      msg = "<div class='buffering-msg align-self-center mb-2'>Too many requests, please wait 60 seconds.</div>";
    }

    buffering.after(msg);
  });
}

function timedRequestCallback() {
  console.log("Automatic update");

  var request = $.ajax({
    url: "https://api.poe.watch/get.php",
    data: {
      league: FILTER.league.name, 
      category: FILTER.category
    },
    type: "GET",
    async: true,
    dataTypes: "json"
  });

  request.done(function(json) {
    console.log("Got " + json.length + " items from request");

    ITEMS = json;
    sortResults();
  });

  request.fail(function(response) {
    $("#searchResults tbody").empty();
    buffering.after("<div class='buffering-msg align-self-center mb-2'>" + response.responseJSON.error + "</div>");
  });
}

//------------------------------------------------------------------------------------------------------------
// Sorting. This can probably be done better. If you know how, let me know.
//------------------------------------------------------------------------------------------------------------

function getSortFunc(col, order) {
  switch (col) {
    case "change":
      return order === "descending" ? sort_changeDesc : sort_changeAsc;
    case "daily":
      return order === "descending" ? sort_dailyDesc  : sort_dailyAsc;
    case "total":
      return order === "descending" ? sort_totalDesc  : sort_totalAsc;
    case "item":
      return order === "descending" ? sort_itemDesc   : sort_itemAsc;
    default:
      return order === "descending" ? sort_priceDesc  : sort_priceAsc;
  }
}

function sort_priceDesc(a, b) {
  if (a.mean > b.mean) return -1;
  if (a.mean < b.mean) return 1;
  return 0;
}

function sort_priceAsc(a, b) {
  if (a.mean < b.mean) return -1;
  if (a.mean > b.mean) return 1;
  return 0;
}

function sort_dailyDesc(a, b) {
  if (a.daily > b.daily) return -1;
  if (a.daily < b.daily) return 1;
  return 0;
}

function sort_dailyAsc(a, b) {
  if (a.daily < b.daily) return -1;
  if (a.daily > b.daily) return 1;
  return 0;
}

function sort_totalDesc(a, b) {
  if (a.total > b.total) return -1;
  if (a.total < b.total) return 1;
  return 0;
}

function sort_totalAsc(a, b) {
  if (a.total < b.total) return -1;
  if (a.total > b.total) return 1;
  return 0;
}

function sort_changeDesc(a, b) {
  if (a.change > b.change) return -1;
  if (a.change < b.change) return 1;
  return 0;
}

function sort_changeAsc(a, b) {
  if (a.change < b.change) return -1;
  if (a.change > b.change) return 1;
  return 0;
}

function sort_itemDesc(a, b) {
  if (a.name > b.name) return -1;
  if (a.name < b.name) return 1;
  return 0;
}

function sort_itemAsc(a, b) {
  if (a.name < b.name) return -1;
  if (a.name > b.name) return 1;
  return 0;
}

//------------------------------------------------------------------------------------------------------------
// Utility functions
//------------------------------------------------------------------------------------------------------------

function formatNum(num) {
  const numberWithCommas = (x) => {
    var parts = x.toString().split(".");
    parts[0] = parts[0].replace(/\B(?=(\d{3})+(?!\d))/g, ",");
    return parts.join(".");
  }

  if (num === null) {
    return 'Unavailable';
  } else return numberWithCommas(Math.round(num * 100) / 100);
}

function updateQueryParam(key, value) {
  switch (key) {
    case "confidence": value = value === false        ? null : value;   break;
    case "search":     value = value === ""           ? null : value;   break;
    case "rarity":     value = value === "all"        ? null : value;   break;
    case "corrupted":  value = value === "all"        ? null : value;   break;
    case "quality":    value = value === "all"        ? null : value;   break;
    case "lvl":        value = value === "all"        ? null : value;   break;
    case "links":      value = value === "none"       ? null : value;   break;
    case "group":      value = value === "all"        ? null : value;   break;
    case "tier":       value = value === "all"        ? null : value;   break;
    case "influence":  value = value === "all"        ? null : value;   break;
    default:           break;
  }
  
  var url = document.location.href;
  var re = new RegExp("([?&])" + key + "=.*?(&|#|$)(.*)", "gi");
  var hash;

  if (re.test(url)) {
    if (typeof value !== 'undefined' && value !== null) {
      url = url.replace(re, '$1' + key + "=" + value + '$2$3');
    } else {
      hash = url.split('#');
      url = hash[0].replace(re, '$1$3').replace(/(&|\?)$/, '');
      
      if (typeof hash[1] !== 'undefined' && hash[1] !== null) {
        url += '#' + hash[1];
      }
    }
  } else if (typeof value !== 'undefined' && value !== null) {
    var separator = url.indexOf('?') !== -1 ? '&' : '?';
    hash = url.split('#');
    url = hash[0] + separator + key + '=' + value;

    if (typeof hash[1] !== 'undefined' && hash[1] !== null) {
      url += '#' + hash[1];
    }
  }

  history.replaceState({}, "foo", url);
}

function parseQueryParam(key) {
  let url = window.location.href;
  key = key.replace(/[\[\]]/g, '\\$&');
  
  var regex = new RegExp('[?&]' + key + '(=([^&#]*)|&|#|$)'),
      results = regex.exec(url);
      
  if (!results   ) return null;
  if (!results[2]) return   '';

  return decodeURIComponent(results[2].replace(/\+/g, ' '));
}

Date.prototype.addDays = function(days) {
  var date = new Date(this.valueOf());
  date.setDate(date.getDate() + days);
  return date;
}

function getServiceLeague(league) {
  for (let i = 0; i < SERVICE_leagues.length; i++) {
    if (SERVICE_leagues[i].name === league) {
      return SERVICE_leagues[i];
    }
  }

  return null;
}

//------------------------------------------------------------------------------------------------------------
// Itetm sorting and searching
//------------------------------------------------------------------------------------------------------------

function sortResults() {
  // Empty the table
  let table = $("#searchResults");
  $("tbody", table).empty();

  let count = 0, matches = 0;
  let buffer = "";

  if (FILTER.sortFunction) {
    ITEMS.sort(FILTER.sortFunction);
  }

  for (let i = 0; i < ITEMS.length; i++) {
    const item = ITEMS[i];

    // Skip parsing if item should be hidden according to filters
    if (checkHideItem(item)) {
      continue;
    }

    matches++;

    // Stop if specified item limit has been reached
    if ( FILTER.parseAmount < 0 || count < FILTER.parseAmount ) {
      // If item has not been parsed, parse it 
      if ( !('tableData' in item) ) {
        item.tableData = new ItemRow(item);
      }

      // Append generated table data to buffer
      buffer += item.tableData.row;
      count++;
    }
  }

  $(".buffering-msg").remove();

  if (count < 1) {
    let msg = "<div class='buffering-msg align-self-center mb-2'>No results</div>";
    $("#buffering-main").after(msg);
  }

  let loadAllBtn = $("#button-showAll");
  if (FILTER.parseAmount > 0 && matches > FILTER.parseAmount) {
    loadAllBtn.text("Show all (" + (matches - FILTER.parseAmount) + " items)");
    loadAllBtn.show();
  } else {
    loadAllBtn.hide();
  }
  
  // Add the generated HTML table data to the table
  table.append(buffer);
}

function checkHideItem(item) {
  // Hide low confidence items
  if (!FILTER.showLowConfidence && FILTER.league.active && item.daily < 5) {
    return true;
  }

  // String search
  if (FILTER.search) {
    if (item.name.toLowerCase().indexOf(FILTER.search) === -1) {
      if (item.type) {
        if (item.type.toLowerCase().indexOf(FILTER.search) === -1) {
          return true;
        }
      } else {
        return true;
      }
    }
  }

  // Hide groups
  if (FILTER.group !== "all" && FILTER.group !== item.group) {
    return true;
  }

  // Hide mismatching rarities
  if (FILTER.rarity) {
    if (FILTER.rarity !== item.frame) {
      return true;
    }
  }

  // Hide items with different links
  if (FILTER.links === null) {
    if (item.links !== null) {
      return true;
    }
  } else if (FILTER.links > 0) {
    if (item.links !== FILTER.links) {
      return true;
    }
  }

  // Sort gems, I guess
  if (FILTER.category === "gem") {
    if (FILTER.gemLvl !== null && item.lvl != FILTER.gemLvl) return true;
    if (FILTER.gemQuality !== null && item.quality != FILTER.gemQuality) return true;
    if (FILTER.gemCorrupted !== null && item.corrupted != FILTER.gemCorrupted) return true;

  } else if (FILTER.category === "map") {
    if (FILTER.tier !== null) {
      if (FILTER.tier === 0) {
        if (item.tier !== null) return true;
      } else if (item.tier !== FILTER.tier) return true;
    }

  } else if (FILTER.category === "base") {
    // Check base influence
    if (FILTER.influence !== null) {
      if (FILTER.influence === "none") {
        if (item.var !== null) return true;
      } else if (FILTER.influence === "either") {
        if (item.var === null) return true;
      } else if (item.var !== FILTER.influence) {
        return true;
      }
    }

    // Check base ilvl
    if (item.ilvl !== null && FILTER.ilvl !== null) {
      if (item.ilvl != FILTER.ilvl) {
        return true;
      }
    }
  }

  return false;
}
