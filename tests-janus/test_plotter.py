import matplotlib.pyplot as plt
import csv
import sys

constraint_white_list = {
    'AtMostOne',  # == DMM 'Absence2',
    # 'End',
    # 'ExactlyOne', # == DMM 'Exactly1',
    'Init',
    'Participation'  # == DMM 'Existence', 
    'AlternatePrecedence',
    'AlternateResponse',
    'ChainPrecedence',
    'ChainResponse',
    'Precedence',
    'RespondedExistence',
    'Response'
}


def get_constraints_vector_mfout(original_file):
    """
    get the constraints and their sup/conf from a CVS generated by MINERful framework
    :param original_file:
    :return:
    """
    try:
        inFile = open(original_file, 'r', newline='')
        csvReader = csv.reader(inFile, delimiter=';', quotechar="'")
    except IndexError:
        print("No input file provided")
        exit(1)

    # constraint(X,Y):sup
    res_map_sup = {}
    # constraint(X,Y):conf
    res_map_conf = {}

    i = 0
    init = True
    for trace_line in csvReader:
        i += 1
        if init:
            init = False
            continue
        if trace_line[1] not in constraint_white_list:
            continue
        res_map_sup[trace_line[0]] = float(trace_line[4])
        res_map_conf[trace_line[0]] = float(trace_line[5])

    # ['Name', 'Constraint template', 'Implying activity', 'Implied activity', 'Support', 'Confidence level','Interest factor']

    return res_map_sup, res_map_conf


def get_constraints_vector_mfvout(original_file):
    """
    get the constraints and their sup/conf from a CVS generated by MINERful framework
    :param original_file:
    :return:
    """
    try:
        inFile = open(original_file, 'r', newline='')
        csvReader = csv.reader(inFile, delimiter=';', quotechar="'")
    except IndexError:
        print("No input file provided")
        exit(1)

    # constraint(X,Y):sup
    res_map_sup = {}
    # constraint(X,Y):conf
    res_map_conf = {}

    init = True
    for trace_line in csvReader:
        if init:
            init = False
            continue
        if trace_line[0] not in constraint_white_list:
            continue
        res_map_sup[trace_line[1]] = float(trace_line[2]) * 100

    # ['Name', 'Constraint template', 'Implying activity', 'Implied activity', 'Support', 'Confidence level','Interest factor']

    return res_map_sup


def get_constraints_vector_dmmout(original_file):
    """
    get the constraints and their sup/conf from a CVS generated by declare miner framework
    :param original_file:
    :return:
    """
    try:
        inFile = open(original_file, 'r', newline='')
        csvReader = csv.reader(inFile, delimiter=':')
    except IndexError:
        print("No input file provided")
        exit(1)

    # constraint(X,Y):sup
    res_map_sup = {}
    # constraint(X,Y):conf
    res_map_conf = {}

    for trace_line in csvReader:
        # preprocessing cleaning
        constraint = str(trace_line[0])
        constraint = constraint.replace("-complete", "")
        constraint = constraint.replace("_", "")

        if constraint.split("(")[0] == 'Existence':
            constraint = 'Participation(' + constraint.split("(")[1].split(",")[0] + ')'
        if constraint.split("(")[0] == 'Init':
            constraint = 'Init(' + constraint.split("(")[1].split(",")[0] + ')'
        if constraint.split("(")[0] == 'Absence2':
            constraint = 'AtMostOne(' + constraint.split("(")[1].split(",")[0] + ')'

        if constraint.split("(")[0] not in constraint_white_list:
            continue
        # data retrieval
        res_map_sup[constraint] = float(trace_line[1]) * 100

    # ['Name', 'Constraint template', 'Implying activity', 'Implied activity', 'Support', 'Confidence level','Interest factor']

    return res_map_sup


def plot_comparison(janus_map_sup, other_map_sup, other_name, measure_janus="sup", measure_other="sup"):
    symbol_generic = '.'
    symbol_prec = '<'
    symbol_max1 = 's'
    symbol_respEx = 'x'
    label_prec = '(Alt./Chain/.)Precedence'
    label_max1 = 'AtMostOne'
    label_respEx = 'RespondedExistence'
    label_generic = "Others"
    alpha = 0.15
    color = 'k'

    for current in janus_map_sup:
        try:
            if "Precedence" in current:
                handler_prec, = plt.plot(other_map_sup[current], janus_map_sup[current], symbol_prec, label=label_prec,
                                         alpha=alpha,
                                         color=color)
            elif "AtMostOne" in current:
                handler_max1, = plt.plot(other_map_sup[current], janus_map_sup[current], symbol_max1, label=label_max1,
                                         alpha=alpha,
                                         color=color)
            elif "RespondedExistence" in current:
                handler_respEx, = plt.plot(other_map_sup[current], janus_map_sup[current], symbol_respEx,
                                           label=label_respEx, alpha=alpha,
                                           color=color)
            else:
                handler_generic, = plt.plot(other_map_sup[current], janus_map_sup[current], symbol_generic,
                                            label=label_generic,
                                            alpha=alpha, color=color)
        except KeyError:
            plt.plot(0, janus_map_sup[current], '+')
    for current in other_map_sup:
        try:
            if "Precedence" in current:
                handler_prec, = plt.plot(other_map_sup[current], janus_map_sup[current], symbol_prec, label=label_prec,
                                         alpha=alpha,
                                         color=color)
            elif "AtMostOne" in current:
                handler_max1, = plt.plot(other_map_sup[current], janus_map_sup[current], symbol_max1, label=label_max1,
                                         alpha=alpha,
                                         color=color)
            elif "RespondedExistence" in current:
                handler_respEx, = plt.plot(other_map_sup[current], janus_map_sup[current], symbol_respEx,
                                           label=label_respEx, alpha=alpha,
                                           color=color)
            else:
                handler_generic, = plt.plot(other_map_sup[current], janus_map_sup[current], symbol_generic,
                                            label=label_generic,
                                            alpha=alpha, color=color)
        except KeyError:
            plt.plot(other_map_sup[current], 0, '+')


    plt.ylabel('Janus ' + measure_janus)
    plt.xlabel(other_name + ' ' + measure_other)
    plt.legend([handler_max1, handler_prec, handler_respEx, handler_generic],
               [label_max1, label_prec, label_respEx, label_generic])

    extension = 'svg'
    plt.savefig('test-Janus-sepsis_J-' + measure_janus + '_' + other_name + '-' + measure_other + '.' + extension)
    # plt.show()
    plt.close()


file_csv_janus = 'test-Janus-sepsis_MODEL_Janus.csv'
file_csv_mf = 'test-Janus-sepsis_MODEL_MINERful.csv'
file_csv_mfv = 'test-Janus-sepsis_MODEL_MINERfulVChk.csv'
file_csv_dmm2 = 'test-Janus-sepsis_MODEL_DMM2.txt'

janus_map_sup, janus_map_conf = get_constraints_vector_mfout(file_csv_janus)
mf_map_sup, mf_map_conf = get_constraints_vector_mfout(file_csv_mf)
mfv_map_sup = get_constraints_vector_mfvout(file_csv_mfv)
dmm2_map_sup = get_constraints_vector_dmmout(file_csv_dmm2)

# Janus Support
plot_comparison(janus_map_sup, mf_map_sup, "Mf")
plot_comparison(janus_map_sup, mf_map_conf, "Mf", "sup", "conf")
plot_comparison(janus_map_sup, mfv_map_sup, "Mf-vchk")
plot_comparison(janus_map_sup, dmm2_map_sup, "DMM2")

# Janus Confidence
plot_comparison(janus_map_conf, mf_map_sup, "Mf", "conf", "sup")
plot_comparison(janus_map_conf, mfv_map_sup, "Mf-vchk", "conf", "sup")
plot_comparison(janus_map_conf, dmm2_map_sup, "DMM2", "conf", "sup")
